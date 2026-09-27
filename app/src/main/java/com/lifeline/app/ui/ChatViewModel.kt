package com.lifeline.app.ui

import android.app.Application
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import com.lifeline.app.mesh.BluetoothMeshDelegate
import com.lifeline.app.mesh.BluetoothMeshService
import com.lifeline.app.mesh.MeshService
import com.lifeline.app.service.MeshServiceHolder
import com.lifeline.app.model.LifeLineMessage


import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.random.Random
import com.lifeline.app.identity.SecureIdentityStateManager
import com.lifeline.app.noise.NoiseSession
import com.lifeline.app.services.ContactDirectory
import com.lifeline.app.services.ContactIdentityResolver
import com.lifeline.app.util.hexEncodedString
import com.lifeline.app.features.voice.LiveVoicePreferences
import com.lifeline.app.features.voice.LiveVoiceTarget
import com.lifeline.app.features.voice.VoiceRecorder

private data class ConversationLiveIdentityState(
    val connectedPeerIDs: List<String>,
    val peerNicknames: Map<String, String>,
    val persistedDisplayNames: Map<String, String>
)

/**
 * Refactored ChatViewModel - Main coordinator for lifeline functionality
 * Delegates specific responsibilities to specialized managers
 */
class ChatViewModel(
    application: Application,
    initialMeshService: BluetoothMeshService,
    initialUnifiedMeshService: MeshService
) : AndroidViewModel(application), BluetoothMeshDelegate {

    // Made var to support mesh service replacement after panic clear
    var meshService: BluetoothMeshService = initialMeshService
        private set
    private var unifiedMeshService: MeshService = initialUnifiedMeshService
    private val mesh: MeshService
        get() = unifiedMeshService
    private val debugManager by lazy { try { com.lifeline.app.ui.debug.DebugSettingsManager.getInstance() } catch (e: Exception) { null } }

    companion object {
        private const val TAG = "ChatViewModel"
        private const val CONVERSATION_DISCONNECT_GRACE_MS = 3_000L
    }

    fun sendVoiceNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
        mediaSendingManager.sendVoiceNote(toPeerIDOrNull, channelOrNull, filePath)
    }

    fun createVoiceRecorder(toPeerIDOrNull: String?, channelOrNull: String?): VoiceRecorder {
        val context = getApplication<Application>().applicationContext
        if (!LiveVoicePreferences.isEnabled(context)) return VoiceRecorder(context)
        val recipientPeerID = toPeerIDOrNull?.let {
            PrivateMediaRecipientResolver.resolve(it, mesh)?.meshPeerID
        }
        val liveTarget = when {
            toPeerIDOrNull != null && recipientPeerID != null && mesh.hasEstablishedSession(recipientPeerID) ->
                LiveVoiceTarget { payload -> mesh.sendVoiceFrame(recipientPeerID, payload) }
            toPeerIDOrNull == null && channelOrNull == null && mesh.getActivePeerCount() > 0 ->
                LiveVoiceTarget { payload -> mesh.sendVoiceFrame(null, payload) }
            else -> null
        }
        return VoiceRecorder(context, liveTarget)
    }

    fun sendFileNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
        mediaSendingManager.sendFileNote(toPeerIDOrNull, channelOrNull, filePath)
    }

    fun sendImageNote(toPeerIDOrNull: String?, channelOrNull: String?, filePath: String) {
        mediaSendingManager.sendImageNote(toPeerIDOrNull, channelOrNull, filePath)
    }

    fun approveLegacyPrivateMedia(requestId: String) {
        mediaSendingManager.approveLegacyPrivateMedia(requestId)
    }

    fun cancelLegacyPrivateMedia(requestId: String) {
        mediaSendingManager.cancelLegacyPrivateMedia(requestId)
    }

    // State management
    private val state = ChatState(
        scope = viewModelScope,
    )

    // Transfer progress tracking
    private val transferMessageMap = mutableMapOf<String, String>()
    private val messageTransferMap = mutableMapOf<String, String>()

    // Specialized managers
    private val dataManager = DataManager(application.applicationContext)
    private val identityManager by lazy { SecureIdentityStateManager(getApplication()) }
    private val seenMessageStore by lazy {
        com.lifeline.app.services.SeenMessageStore.getInstance(getApplication())
    }
    private val conversationListPreferences =
        com.lifeline.app.services.ConversationListPreferences.getInstance(getApplication())
    private val messageManager = MessageManager(state)
    private val channelManager = ChannelManager(
        state,
        messageManager,
        dataManager,
        viewModelScope
    )

    // Create Noise session delegate for clean dependency injection
    private val noiseSessionDelegate = object : NoiseSessionDelegate {
        override fun hasEstablishedSession(peerID: String): Boolean = hasEstablishedSessionOnAnyLocalTransport(peerID)
        override fun initiateHandshake(peerID: String) = initiateNoiseHandshakeOnBestLocalTransport(peerID)
        override fun getMyPeerID(): String = mesh.myPeerID
    }

    val privateChatManager = PrivateChatManager(
        state,
        messageManager,
        dataManager,
        noiseSessionDelegate,
        hasReadReceiptBeenSent = { messageID ->
            seenMessageStore.hasReadReceiptBeenSent(messageID)
        },
        markMessageReadLocally = { messageID ->
            seenMessageStore.markReadLocally(messageID)
        }
    )
    private val notificationManager = NotificationManager(
      application.applicationContext,
      NotificationManagerCompat.from(application.applicationContext)
    )


    // Media file sending manager
    private val mediaSendingManager = MediaSendingManager(
        state,
        messageManager,
        channelManager,
        viewModelScope
    ) { mesh }
    
    // Delegate handler for mesh callbacks
    private val meshDelegateHandler = MeshDelegateHandler(
        state = state,
        messageManager = messageManager,
        channelManager = channelManager,
        privateChatManager = privateChatManager,
        notificationManager = notificationManager,
        coroutineScope = viewModelScope,
        onHapticFeedback = { ChatViewModelUtils.triggerHapticFeedback(application.applicationContext) },
        getMyPeerID = { mesh.myPeerID },
        getMeshService = { mesh },
        markMessageReadLocally = { messageID ->
            seenMessageStore.markReadLocally(messageID)
        }
    )
    

    val messages: StateFlow<List<LifeLineMessage>> = state.messages
    val connectedPeers: StateFlow<List<String>> = state.connectedPeers
    val nickname: StateFlow<String> = state.nickname
    val isConnected: StateFlow<Boolean> = state.isConnected
    val privateChats: StateFlow<Map<String, List<LifeLineMessage>>> = state.privateChats
    val selectedPrivateChatPeer: StateFlow<String?> = state.selectedPrivateChatPeer
    val unreadPrivateMessages: StateFlow<Set<String>> = state.unreadPrivateMessages
    internal val conversationStoreState =
        com.lifeline.app.services.AppStateStore.conversationStoreState
    private val conversationPresencePeers = MutableStateFlow<List<String>>(emptyList())
    private val conversationPresenceRemovalJobs = mutableMapOf<String, Job>()
    private val conversationDirectoryRevision = MutableStateFlow(0L)
    private fun refreshConversationDirectoryState() {
        viewModelScope.launch {
            conversationListPreferences.canonicalizeAliases()
            conversationDirectoryRevision.update { it + 1L }
        }
    }

    private val conversationLiveIdentityState = combine(
        conversationPresencePeers,
        state.peerNicknames,
        state.peerFingerprints,
        conversationDirectoryRevision,
        com.lifeline.app.services.AppStateStore.privateConversationDisplayNames
    ) { connectedPeerIDs, peerNicknames, _, _, persistedDisplayNames ->
        ConversationLiveIdentityState(
            connectedPeerIDs = connectedPeerIDs,
            peerNicknames = peerNicknames,
            persistedDisplayNames = persistedDisplayNames
                .mapKeys { (conversationID, _) -> conversationID.lowercase() }
        )
    }
    private val baseConversations = combine(
        state.unreadPrivateMessages,
        state.privateChats,
        state.nickname,
        conversationLiveIdentityState,
        com.lifeline.app.services.AppStateStore.unreadPrivateMessageCounts
    ) { unreadConversationIDs, chats, currentNickname, liveIdentity, unreadCounts ->
        val seenStore = seenMessageStore
        val connectedPeerByIdentity = buildMap {
            liveIdentity.connectedPeerIDs.forEach { peerID ->
                val identities = runCatching {
                    ContactDirectory.aliasesForConversation(peerID) +
                        ContactDirectory.canonicalConversationId(peerID)
                }.getOrDefault(setOf(peerID))
                identities.forEach { identity ->
                    putIfAbsent(identity.lowercase(), peerID)
                }
            }
        }
        buildConversationSummaries(
            unreadConversationIDs = unreadConversationIDs,
            privateChats = chats,
            currentUserIdentifiers = setOf(currentNickname, mesh.myPeerID),
            canonicalize = ContactDirectory::canonicalConversationId,
            isMessageRead = { message ->
                com.lifeline.app.services.AppStateStore.isPrivateMessageRead(message.id) ||
                    seenStore.hasBeenReadLocally(message.id)
            },
            persistedUnreadCounts = unreadCounts
        ).map { summary ->
            val resolution = ContactDirectory.resolve(summary.conversationID)
            val aliases = buildSet {
                addAll(summary.identityAliases)
                add(summary.conversationID)
                add(resolution.conversationID)
                resolution.meshPeerID?.let(::add)
                resolution.noiseKeyHex?.let(::add)
            }.mapTo(mutableSetOf()) { it.lowercase() }
            val connectedPeerID = aliases
                .asSequence()
                .mapNotNull(connectedPeerByIdentity::get)
                .firstOrNull()
            val persistedDisplayName = liveIdentity.persistedDisplayNames[
                summary.conversationID.lowercase()
            ] ?: aliases
                .asSequence()
                .mapNotNull(liveIdentity.persistedDisplayNames::get)
                .firstOrNull()

            summary.copy(
                displayName = resolveConversationDisplayName(
                    fallbackName = summary.displayName,
                    connectedPeerID = connectedPeerID,
                    peerNicknames = liveIdentity.peerNicknames,
                    resolvedContactName = resolution.displayName,
                    persistedDisplayName = persistedDisplayName
                ),
                identityAliases = aliases,
                isConnected = connectedPeerID != null,
                connectedPeerID = connectedPeerID
            )
        }
    }

    internal val conversations: StateFlow<List<ConversationSummary>> = combine(
        baseConversations,
        conversationListPreferences.pinned,
        conversationListPreferences.muted,
        conversationListPreferences.drafts
    ) { summaries, pinned, muted, drafts ->
        sortConversationSummaries(
            summaries.map { summary ->
                val key = summary.conversationID.lowercase()
                summary.copy(
                    isPinned = key in pinned,
                    isMuted = key in muted,
                    draft = drafts[key]
                )
            }
        )
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )
    val joinedChannels: StateFlow<Set<String>> = state.joinedChannels
    val currentChannel: StateFlow<String?> = state.currentChannel
    val channelMessages: StateFlow<Map<String, List<LifeLineMessage>>> = state.channelMessages
    val unreadChannelMessages: StateFlow<Map<String, Int>> = state.unreadChannelMessages
    val passwordProtectedChannels: StateFlow<Set<String>> = state.passwordProtectedChannels
    val showPasswordPrompt: StateFlow<Boolean> = state.showPasswordPrompt
    val passwordPromptChannel: StateFlow<String?> = state.passwordPromptChannel
    val hasUnreadChannels = state.hasUnreadChannels
    val hasUnreadPrivateMessages = state.hasUnreadPrivateMessages
    val showMentionSuggestions: StateFlow<Boolean> = state.showMentionSuggestions
    val mentionSuggestions: StateFlow<List<String>> = state.mentionSuggestions
    val peerSessionStates: StateFlow<Map<String, String>> = state.peerSessionStates
    val peerFingerprints: StateFlow<Map<String, String>> = state.peerFingerprints
    val peerNicknames: StateFlow<Map<String, String>> = state.peerNicknames
    val peerRSSI: StateFlow<Map<String, Int>> = state.peerRSSI
    val peerDirect: StateFlow<Map<String, Boolean>> = state.peerDirect
    val showAppInfo: StateFlow<Boolean> = state.showAppInfo
    val privateChatSheetPeer: StateFlow<String?> = state.privateChatSheetPeer
    val legacyPrivateMediaConsent: StateFlow<LegacyPrivateMediaConsentRequest?> =
        mediaSendingManager.legacyPrivateMediaConsent
    val meshServiceFacade: MeshService
        get() = mesh
    val myPeerID: String
        get() = mesh.myPeerID

    fun getMeshPeerFingerprint(peerID: String): String? = mesh.getPeerFingerprint(peerID)

    init {
        observeConversationPresenceWithDisconnectGrace()
        // Note: Mesh service delegate is now set by MainActivity
        loadAndInitialize()
        ContactDirectory.initialize(getApplication()) { mesh }
        com.lifeline.app.services.AppStateStore.canonicalizePrivateChats()
        observeConversationDisplayNames()
        // Application startup performs the initial restore. Repeat it for every new UI owner
        // because a quick reopen can reuse a process whose in-memory state was cleared during
        // controlled shutdown.
        com.lifeline.app.services.AppStateStore.reloadConversationPersistence(
            getApplication()
        )
        // Mark queued private messages as failed when the router gives up on them
        try {
            com.lifeline.app.services.MessageRouter.getInstance(getApplication(), mesh).onMessageExpired = { messageID ->
                messageManager.updateMessageDeliveryStatus(
                    messageID,
                    com.lifeline.app.model.DeliveryStatus.Failed("Message expired before delivery")
                )
            }
        } catch (_: Exception) { }
        // Hydrate UI state from process-wide AppStateStore to survive Activity recreation
        viewModelScope.launch {
            try { com.lifeline.app.services.AppStateStore.peers.collect { peers ->
                state.setConnectedPeers(peers)
                state.setIsConnected(peers.isNotEmpty())
            } } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try { com.lifeline.app.services.AppStateStore.publicMessages.collect { msgs ->
                // Source of truth is AppStateStore; replace to avoid duplicate keys in LazyColumn
                state.setMessages(msgs)
            } } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try {
                combine(
                    com.lifeline.app.services.AppStateStore.privateMessages,
                    com.lifeline.app.services.AppStateStore.unreadPrivateMessageCounts
                ) { byPeer, unreadCounts -> byPeer to unreadCounts }
                    .collect { (byPeer, unreadCounts) ->
                val (canonicalChats, unreadConversationIDs) = withContext(Dispatchers.IO) {
                    val canonical = ContactDirectory.canonicalizePrivateChats(byPeer)
                    val unread = try {
                        val myNick = state.getNicknameValue().ifBlank { mesh.myPeerID }
                        canonical
                            .filterValues { messages ->
                                messages.any { message ->
                                    message.sender != myNick &&
                                        message.sender != "system" &&
                                        !com.lifeline.app.services.AppStateStore
                                            .isPrivateMessageRead(message.id) &&
                                        !seenMessageStore.hasBeenReadLocally(message.id)
                                }
                            }
                            .keys + unreadCounts
                                .filterValues { it > 0 }
                                .keys
                    } catch (_: Exception) {
                        state.getUnreadPrivateMessagesValue()
                    }
                    canonical to unread
                }
                state.setPrivateChats(canonicalChats)
                // Recompute unread set using SeenMessageStore for robustness across Activity recreation
                state.setUnreadPrivateMessages(unreadConversationIDs)
            } } catch (_: Exception) { }
        }
        viewModelScope.launch {
            try { com.lifeline.app.services.AppStateStore.channelMessages.collect { byChannel ->
                // Replace with store snapshot
                state.setChannelMessages(byChannel)
            } } catch (_: Exception) { }
        }
        // Subscribe to BLE transfer progress and reflect in message deliveryStatus
        viewModelScope.launch {
            com.lifeline.app.mesh.TransferProgressManager.events.collect { evt ->
                mediaSendingManager.handleTransferProgressEvent(evt)
            }
        }
        
        // Removed background location notes subscription. Notes now load only when sheet opens.
    }

    /**
     * Mesh discovery can briefly drop a peer while transports hand over. Preserve its online
     * treatment for a short grace window to keep conversation rows from jumping between sections.
     * New connections still appear immediately.
     */
    private fun observeConversationPresenceWithDisconnectGrace() {
        viewModelScope.launch {
            state.connectedPeers.collect { connected ->
                val current = connected.toSet()
                current.forEach { peerID ->
                    conversationPresenceRemovalJobs.remove(peerID)?.cancel()
                }

                val displayed = conversationPresencePeers.value.toMutableList()
                connected.forEach { peerID ->
                    if (peerID !in displayed) displayed.add(peerID)
                }
                if (displayed != conversationPresencePeers.value) {
                    conversationPresencePeers.value = displayed
                }

                (displayed.toSet() - current).forEach { peerID ->
                    if (peerID in conversationPresenceRemovalJobs) return@forEach
                    conversationPresenceRemovalJobs[peerID] = launch {
                        delay(CONVERSATION_DISCONNECT_GRACE_MS)
                        if (peerID !in state.connectedPeers.value) {
                            conversationPresencePeers.value =
                                conversationPresencePeers.value - peerID
                        }
                        conversationPresenceRemovalJobs.remove(peerID)
                    }
                }
            }
        }
    }

    private fun observeConversationDisplayNames() {
        viewModelScope.launch {
            combine(
                state.peerNicknames,
                state.connectedPeers,
                state.peerFingerprints
            ) { peerNicknames, connectedPeers, _ ->
                connectedPeers.mapNotNull { peerID ->
                    peerNicknames[peerID]?.let { peerID to it }
                }.toMap()
            }.collect { connectedNames ->
                conversationListPreferences.canonicalizeAliases()
                com.lifeline.app.services.AppStateStore
                    .updatePrivateConversationDisplayNames(connectedNames)
            }
        }
    }

    fun cancelMediaSend(messageId: String) {
        // Delegate to MediaSendingManager which tracks transfer IDs and cleans up UI state
        mediaSendingManager.cancelMediaSend(messageId)
    }
    
    private fun loadAndInitialize() {
        // Load nickname
        val nickname = dataManager.loadNickname()
        state.setNickname(nickname)
        
        // Load data
        val (joinedChannels, protectedChannels) = channelManager.loadChannelData()
        state.setJoinedChannels(joinedChannels)
        state.setPasswordProtectedChannels(protectedChannels)
        
        // Initialize channel messages
        joinedChannels.forEach { channel ->
            if (!state.getChannelMessagesValue().containsKey(channel)) {
                val updatedChannelMessages = state.getChannelMessagesValue().toMutableMap()
                updatedChannelMessages[channel] = emptyList()
                state.setChannelMessages(updatedChannelMessages)
            }
        }
        
        // Load other data
        dataManager.loadFavorites()
        state.setFavoritePeers(dataManager.favoritePeers.toSet())
        dataManager.loadBlockedUsers()
        
        // Initialize session state monitoring
        initializeSessionStateMonitoring()

        // Bridge DebugSettingsManager -> Chat messages when verbose logging is on
        viewModelScope.launch {
            com.lifeline.app.ui.debug.DebugSettingsManager.getInstance().debugMessages.collect { msgs ->
                if (com.lifeline.app.ui.debug.DebugSettingsManager.getInstance().verboseLoggingEnabled.value) {
                    // Append only latest debug message as system message to avoid flooding
                    msgs.lastOrNull()?.let { dm ->
                        messageManager.addSystemMessage(dm.content)
                    }
                }
            }
        }
        
        // Note: Mesh service is now started by MainActivity

        // BLE receives are inserted by the MessageHandler path.
    }
    
    override fun onCleared() {
        com.lifeline.app.services.AppStateStore.setSelectedPrivateChatPeer(null)
        // Note: Mesh service lifecycle is now managed by MainActivity
    }
    
    // Nickname Management
    
    fun setNickname(newNickname: String) {
        state.setNickname(newNickname)
        dataManager.saveNickname(newNickname)
        mesh.sendBroadcastAnnounce()
    }
    

    // Channel Management (delegated)
    
    fun joinChannel(channel: String, password: String? = null): Boolean {
        return channelManager.joinChannel(channel, password, mesh.myPeerID)
    }
    
    fun switchToChannel(channel: String?) {
        channelManager.switchToChannel(channel)
    }
    
    fun leaveChannel(channel: String) {
        channelManager.leaveChannel(channel)
        mesh.sendMessage("left $channel", emptyList(), null)
    }
    
    // Private Chat Management (delegated)
    
    suspend fun startPrivateChat(peerID: String) {
        val (conversationID, success) = withContext(Dispatchers.IO) {
            val canonicalID = ContactDirectory.canonicalConversationId(peerID)
            com.lifeline.app.services.AppStateStore
                .loadPrivateConversationHistory(canonicalID)
            state.setPrivateChats(
                ContactDirectory.canonicalizePrivateChats(
                    com.lifeline.app.services.AppStateStore.privateMessages.value
                )
            )
            val unreadAliases = matchingUnreadAliases(
                unreadConversationIDs = state.getUnreadPrivateMessagesValue(),
                canonicalConversationID = canonicalID,
                canonicalize = ContactDirectory::canonicalConversationId
            )
            canonicalID to privateChatManager.startPrivateChat(
                peerID = canonicalID,
                meshService = mesh,
                unreadAliases = unreadAliases
            )
        }
        if (success) {
            // Notify notification manager about current private chat
            setCurrentPrivateChatPeer(conversationID)
            // Clear notifications for this sender since user is now viewing the chat
            clearNotificationsForSender(conversationID)
        }
    }
    
    fun endPrivateChat() {
        val conversationID = state.getSelectedPrivateChatPeerValue()
        privateChatManager.endPrivateChat()
        if (conversationID != null) {
            com.lifeline.app.services.AppStateStore
                .releasePrivateConversationHistory(conversationID)
            state.setPrivateChats(
                ContactDirectory.canonicalizePrivateChats(
                    com.lifeline.app.services.AppStateStore.privateMessages.value
                )
            )
        }
        // Notify notification manager that no private chat is active
        setCurrentPrivateChatPeer(null)
        // Clear mesh mention notifications since user is now back in mesh chat
        clearMeshMentionNotifications()
        // Ensure sheet is hidden
        hidePrivateChatSheet()
    }

    internal suspend fun deletePrivateConversation(
        peerOrConversationID: String
    ): com.lifeline.app.services.DeletedPrivateConversation? {
        val canonicalID = ContactDirectory.canonicalConversationId(peerOrConversationID)
        val wasPinned = conversationListPreferences.isPinned(canonicalID)
        val wasMuted = conversationListPreferences.isMuted(canonicalID)
        val draft = conversationListPreferences.draftFor(canonicalID)
        val unreadAliases = matchingUnreadAliases(
            unreadConversationIDs = state.getUnreadPrivateMessagesValue(),
            canonicalConversationID = canonicalID,
            canonicalize = ContactDirectory::canonicalConversationId
        )
        val deletion = withContext(Dispatchers.IO) {
            com.lifeline.app.services.AppStateStore
                .deletePrivateConversationAndWait(canonicalID)
        }?.copy(
            wasPinned = wasPinned,
            wasMuted = wasMuted,
            draft = draft
        ) ?: return null
        conversationListPreferences.removeConversation(canonicalID)

        state.setPrivateChats(
            ContactDirectory.canonicalizePrivateChats(
                com.lifeline.app.services.AppStateStore.privateMessages.value
            )
        )
        state.setUnreadPrivateMessages(
            state.getUnreadPrivateMessagesValue() - unreadAliases
        )
        seenMessageStore.remove(deletion.messageIDs)

        val selected = state.getSelectedPrivateChatPeerValue()
        if (
            selected != null &&
            ContactDirectory.canonicalConversationId(selected)
                .equals(canonicalID, ignoreCase = true)
        ) {
            privateChatManager.endPrivateChat()
            setCurrentPrivateChatPeer(null)
        }
        val sheetPeer = state.getPrivateChatSheetPeerValue()
        if (
            sheetPeer != null &&
            ContactDirectory.canonicalConversationId(sheetPeer)
                .equals(canonicalID, ignoreCase = true)
        ) {
            hidePrivateChatSheet()
        }
        clearNotificationsForSender(canonicalID)
        notificationManager.removeConversationShortcut(canonicalID)
        return deletion
    }

    internal suspend fun restoreDeletedConversation(
        deletion: com.lifeline.app.services.DeletedPrivateConversation
    ): Boolean {
        val restored = withContext(Dispatchers.IO) {
            com.lifeline.app.services.AppStateStore
                .restoreDeletedConversation(deletion)
        }
        if (!restored) return false
        if (deletion.wasPinned != conversationListPreferences.isPinned(deletion.conversationID)) {
            conversationListPreferences.togglePinned(deletion.conversationID)
        }
        if (deletion.wasMuted != conversationListPreferences.isMuted(deletion.conversationID)) {
            conversationListPreferences.toggleMuted(deletion.conversationID)
        }
        deletion.draft?.let {
            conversationListPreferences.setDraft(deletion.conversationID, it)
        }
        state.setPrivateChats(
            ContactDirectory.canonicalizePrivateChats(
                com.lifeline.app.services.AppStateStore.privateMessages.value
            )
        )
        if (deletion.unreadMessageCount > 0) {
            state.setUnreadPrivateMessages(
                state.getUnreadPrivateMessagesValue() + deletion.conversationID
            )
        }
        return true
    }

    internal suspend fun setConversationRead(
        conversationID: String,
        isRead: Boolean
    ): Boolean {
        val canonicalID = ContactDirectory.canonicalConversationId(conversationID)
        val updated = withContext(Dispatchers.IO) {
            com.lifeline.app.services.AppStateStore
                .setPrivateConversationRead(canonicalID, isRead)
        }
        if (!updated) return false
        state.setUnreadPrivateMessages(
            if (isRead) {
                state.getUnreadPrivateMessagesValue().filterNotTo(mutableSetOf()) {
                    ContactDirectory.canonicalConversationId(it)
                        .equals(canonicalID, ignoreCase = true)
                }
            } else {
                state.getUnreadPrivateMessagesValue() + canonicalID
            }
        )
        return true
    }

    // Open Latest Unread Private Chat

    fun openLatestUnreadPrivateChat() {
        try {
            val unreadKeys = state.getUnreadPrivateMessagesValue()
            if (unreadKeys.isEmpty()) return

            val me = state.getNicknameValue() ?: mesh.myPeerID
            val chats = state.getPrivateChatsValue()

            // Pick the latest incoming message among unread conversations
            var bestKey: String? = null
            var bestTime: Long = Long.MIN_VALUE

            unreadKeys.forEach { key ->
                val list = chats[key]
                if (!list.isNullOrEmpty()) {
                    // Prefer the latest incoming message (sender != me), fallback to last message
                    val latestIncoming = list.lastOrNull { it.sender != me }
                    val candidateTime = (latestIncoming ?: list.last()).timestamp.time
                    if (candidateTime > bestTime) {
                        bestTime = candidateTime
                        bestKey = key
                    }
                }
            }

            val targetKey = bestKey ?: unreadKeys.firstOrNull() ?: return

            // Resolve to a canonical mesh peer if needed
            val openPeer: String = com.lifeline.app.services.ConversationAliasResolver.resolveCanonicalPeerID(
                selectedPeerID = targetKey,
                connectedPeers = state.getConnectedPeersValue(),
                meshNoiseKeyForPeer = { pid -> mesh.getPeerInfo(pid)?.noisePublicKey }
            )

            showPrivateChatSheet(openPeer)
        } catch (e: Exception) {
            Log.w(TAG, "openLatestUnreadPrivateChat failed: ${e.message}")
        }
    }

    // END - Open Latest Unread Private Chat

    
    // Message Sending
    
    fun sendMessage(
        content: String,
        onAccepted: (Boolean) -> Unit = {}
    ) {
        if (content.isEmpty()) {
            onAccepted(false)
            return
        }
        
        val mentions = messageManager.parseMentions(content, mesh.getPeerNicknames().values.toSet(), state.getNicknameValue())
        var selectedPeer = state.getSelectedPrivateChatPeerValue()
        val currentChannelValue = state.getCurrentChannelValue()
        
        if (selectedPeer != null) {
            // If the selected peer is a noise-hex identity, resolve to a canonical target
            selectedPeer = ContactDirectory.canonicalConversationId(
                com.lifeline.app.services.ConversationAliasResolver.resolveCanonicalPeerID(
                selectedPeerID = selectedPeer,
                connectedPeers = state.getConnectedPeersValue(),
                meshNoiseKeyForPeer = { pid -> mesh.getPeerInfo(pid)?.noisePublicKey }
                )
            ).also { canonical ->
                if (canonical != state.getSelectedPrivateChatPeerValue()) {
                    privateChatManager.startPrivateChat(canonical, mesh)
                    // If we're in the private chat sheet, update its active peer too
                    if (state.getPrivateChatSheetPeerValue() != null) {
                        showPrivateChatSheet(canonical)
                    }
                }
            }
            // Send private message
            val recipientNickname = nicknameForPeer(selectedPeer)
            val destination = selectedPeer
            viewModelScope.launch {
                val accepted = privateChatManager.sendPrivateMessageDurably(
                    content,
                    destination,
                    recipientNickname,
                    state.getNicknameValue(),
                    mesh.myPeerID
                ) { messageContent, peerID, recipientNicknameParam, messageId ->
                    val router = com.lifeline.app.services.MessageRouter.getInstance(
                        getApplication(),
                        mesh
                    )
                    val route = router.sendPrivate(
                        messageContent,
                        peerID,
                        recipientNicknameParam,
                        messageId
                    )

                }
                onAccepted(accepted)
            }
        } else {
            // Send public/channel message via mesh
            val message = LifeLineMessage(
                sender = state.getNicknameValue() ?: mesh.myPeerID,
                content = content,
                timestamp = Date(),
                isRelay = false,
                senderPeerID = mesh.myPeerID,
                mentions = if (mentions.isNotEmpty()) mentions else null,
                channel = currentChannelValue
            )

            if (currentChannelValue != null) {
                channelManager.addChannelMessage(currentChannelValue, message, mesh.myPeerID)

                // Check if encrypted channel
                if (channelManager.hasChannelKey(currentChannelValue)) {
                    channelManager.sendEncryptedChannelMessage(
                        content,
                        mentions,
                        currentChannelValue,
                        state.getNicknameValue(),
                        mesh.myPeerID,
                        onEncryptedPayload = { encryptedData ->
                            mesh.sendMessage(content, mentions, currentChannelValue)
                        },
                        onFallback = {
                            mesh.sendMessage(content, mentions, currentChannelValue)
                        }
                    )
                } else {
                    mesh.sendMessage(content, mentions, currentChannelValue)
                }
            } else {
                messageManager.addMessage(message)
                mesh.sendMessage(content, mentions, null)
            }
            onAccepted(true)
        }
    }

    // Utility Functions
    
    fun getPeerIDForNickname(nickname: String): String? {
        return mesh.getPeerNicknames().entries.find { it.value == nickname }?.key
    }
    
    fun toggleFavorite(peerID: String) {
        Log.d("ChatViewModel", "toggleFavorite called for peerID: $peerID")
        privateChatManager.toggleFavorite(peerID)
    }

    private fun hasEstablishedSessionOnMesh(peerID: String): Boolean {
        return try {
            mesh.getPeerInfo(peerID)?.isConnected == true &&
                mesh.hasEstablishedSession(peerID)
        } catch (_: Exception) {
            false
        }
    }

    private fun hasEstablishedSessionOnAnyLocalTransport(peerID: String): Boolean {
        return hasEstablishedSessionOnMesh(peerID)
    }

    private fun initiateNoiseHandshakeOnBestLocalTransport(peerID: String) {
        mesh.initiateNoiseHandshake(peerID)
    }

    private fun nicknameForPeer(peerID: String): String? {
        val contact = ContactDirectory.resolve(peerID)
        val meshPeerID = contact.meshPeerID ?: peerID
        return contact.displayName
            ?: state.peerNicknames.value[meshPeerID]
            ?: try { mesh.getPeerNicknames()[meshPeerID] } catch (_: Exception) { null }
    }

    private fun sessionStateForPeer(peerID: String): NoiseSession.NoiseSessionState {
        return try { mesh.getSessionState(peerID) } catch (_: Exception) { NoiseSession.NoiseSessionState.Uninitialized }
    }
    
    /**
     * Initialize session state monitoring for reactive UI updates
     */
    private fun initializeSessionStateMonitoring() {
        viewModelScope.launch {
            while (true) {
                delay(1000) // Check session states every second
                updateReactiveStates()
            }
        }
    }
    
    // Location notes subscription management moved to LocationNotesViewModelExtensions.kt
    
    /**
     * Update reactive states for all connected peers (session states, fingerprints, nicknames, RSSI)
     */
    private fun updateReactiveStates() {
        val currentPeers = state.getConnectedPeersValue()
        
        // Update session states
        val prevStates = state.getPeerSessionStatesValue()
        val sessionStates = currentPeers.associateWith { peerID ->
            sessionStateForPeer(peerID).toString()
        }
        state.setPeerSessionStates(sessionStates)
        // Detect new established sessions and flush router outbox for them and their noiseHex aliases
        sessionStates.forEach { (peerID, newState) ->
            val old = prevStates[peerID]
            if (old != "established" && newState == "established") {
                com.lifeline.app.services.MessageRouter
                    .getInstance(getApplication(), mesh)
                    .onSessionEstablished(peerID)
            }
        }
        // Update fingerprint mappings from centralized manager
        val fingerprints = privateChatManager.getAllPeerFingerprints()
        state.setPeerFingerprints(fingerprints)
        fingerprints.forEach { (peerID, fingerprint) ->
            identityManager.cachePeerFingerprint(peerID, fingerprint)
            val info = try { mesh.getPeerInfo(peerID) } catch (_: Exception) { null }
            val noiseKeyHex = info?.noisePublicKey?.hexEncodedString()
            if (noiseKeyHex != null) {
                identityManager.cachePeerNoiseKey(peerID, noiseKeyHex)
                identityManager.cacheNoiseFingerprint(noiseKeyHex, fingerprint)
            }
            info?.nickname?.takeIf { it.isNotBlank() }?.let { nickname ->
                identityManager.cacheFingerprintNickname(fingerprint, nickname)
            }
        }

        state.setPeerNicknames(mesh.getPeerNicknames())

        state.setPeerRSSI(mesh.getPeerRSSI())

        // Update directness per peer (driven by PeerManager state)
        try {
            val directMap = state.getConnectedPeersValue().associateWith { pid ->
                mesh.getPeerInfo(pid)?.isDirectConnection == true
            }
            state.setPeerDirect(directMap)
        } catch (_: Exception) { }

    }

    // Debug and Troubleshooting
    
    fun getDebugStatus(): String {
        return mesh.getDebugStatus()
    }
    
    fun setCurrentPrivateChatPeer(peerID: String?) {
        notificationManager.setCurrentPrivateChatPeer(peerID)
    }
    

    fun clearNotificationsForSender(peerID: String) {
        notificationManager.clearNotificationsForSender(peerID)
    }
    

    fun clearMeshMentionNotifications() {
        notificationManager.clearMeshMentionNotifications()
    }


    fun showPrivateChatSheet(peerID: String) {
        val conversationID = ContactDirectory.canonicalConversationId(peerID)
        state.setPrivateChatSheetPeer(conversationID)
    }

    fun hidePrivateChatSheet() {
        state.setPrivateChatSheetPeer(null)
    }


    // BluetoothMeshDelegate Implementation (delegated)
    
    override fun didReceiveMessage(message: LifeLineMessage) {
        meshDelegateHandler.didReceiveMessage(message)
    }
    
    override fun didUpdatePeerList(peers: List<String>) {
        meshDelegateHandler.didUpdatePeerList(peers)
    }

    override fun didReceiveChannelLeave(channel: String, fromPeer: String) {
        meshDelegateHandler.didReceiveChannelLeave(channel, fromPeer)
    }
    
    override fun didReceiveDeliveryAck(messageID: String, recipientPeerID: String) {
        meshDelegateHandler.didReceiveDeliveryAck(messageID, recipientPeerID)
    }
    
    override fun didReceiveReadReceipt(messageID: String, recipientPeerID: String) {
        meshDelegateHandler.didReceiveReadReceipt(messageID, recipientPeerID)
    }

    override fun didReceiveVerifyChallenge(peerID: String, payload: ByteArray, timestampMs: Long) { }
    override fun didReceiveVerifyResponse(peerID: String, payload: ByteArray, timestampMs: Long) { }

    override fun didResolvePrivateMediaPolicy(peerID: String) {
        mediaSendingManager.retryPendingPrivateMedia(peerID)
    }
    
    override fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String? {
        return meshDelegateHandler.decryptChannelMessage(encryptedContent, channel)
    }
    
    override fun getNickname(): String? {
        return meshDelegateHandler.getNickname()
    }
    
    override fun isFavorite(peerID: String): Boolean {
        return meshDelegateHandler.isFavorite(peerID)
    }
    
    // Emergency Clear

    private var panicClearInProgress = false

    fun panicClearAllData() {
        if (panicClearInProgress) return
        panicClearInProgress = true
        viewModelScope.launch {
            try {
                performPanicClearAllData()
            } finally {
                panicClearInProgress = false
            }
        }
    }

    private suspend fun performPanicClearAllData() {
        Log.w(TAG, "🚨 PANIC MODE ACTIVATED - Clearing all sensitive data")

        // A pending one-shot downgrade confirmation must not survive panic or
        // become actionable against the fresh post-wipe identity.
        mediaSendingManager.clearPendingPrivateMediaConsent()

        // Stop all message admission before wiping storage. The AppStateStore gate also rejects
        // any transport callback already in flight until the fresh identity is ready.
        clearAllMeshServiceData()
        val conversationsCleared =
            com.lifeline.app.services.AppStateStore
                .panicClearPrivateConversations()

        // Clear all UI managers
        com.lifeline.app.services.AppStateStore.clear()
        messageManager.clearAllMessages()
        channelManager.clearAllChannels()
        privateChatManager.clearAllPrivateChats()
        dataManager.clearAllData()
        conversationListPreferences.clearAll()
        
        // Clear seen message store and MessageRouter outbox
        try {
            com.lifeline.app.services.SeenMessageStore.getInstance(getApplication()).clear()
        } catch (_: Exception) { }
        try {
            com.lifeline.app.services.MessageRouter.tryGetInstance()?.clearAll()
        } catch (_: Exception) { }
        
        // Clear all cryptographic data
        clearAllCryptographicData()
        
        // Clear all notifications
        notificationManager.clearAllNotifications(removeConversationShortcuts = true)

        // Clear all media files
        com.lifeline.app.features.file.FileUtils.clearAllMedia(getApplication())
        

        // Reset nickname
        val newNickname = "anon${Random.nextInt(1000, 9999)}"
        state.setNickname(newNickname)
        dataManager.saveNickname(newNickname)

        if (!conversationsCleared) {
            // Privacy wins over availability: keep private-message admission and transports
            // stopped if SQLite could not prove that the conversation history was erased.
            Log.e(TAG, "🚨 PANIC MODE INCOMPLETE - conversation database wipe failed")
            return
        }

        // Recreate mesh service with fresh identity
        com.lifeline.app.services.AppStateStore
            .resumePrivateConversationsAfterPanic()
        recreateMeshServiceAfterPanic()

        Log.w(TAG, "🚨 PANIC MODE COMPLETED - New identity: ${mesh.myPeerID}")
    }

    /**
     * Recreate the mesh service with a fresh identity after panic clear.
     * This ensures the new cryptographic keys are used for a new peer ID.
     */
    private fun recreateMeshServiceAfterPanic() {
        val oldPeerID = mesh.myPeerID

        // Clear the holder so getOrCreate() returns a fresh instance
        MeshServiceHolder.clear()

        // Create fresh mesh service with new identity (keys were regenerated in clearAllCryptographicData)
        val freshMeshService = MeshServiceHolder.getOrCreate(getApplication())
        val freshUnifiedMeshService = MeshServiceHolder.getUnifiedOrCreate(getApplication())

        // Replace our reference and set up the new service
        meshService = freshMeshService
        unifiedMeshService = freshUnifiedMeshService
        mesh.delegate = this

        // Restart mesh operations with new identity
        mesh.startServices()
        mesh.sendBroadcastAnnounce()

        Log.d(
            TAG,
            "✅ Mesh service recreated. Old peerID: $oldPeerID, New peerID: ${mesh.myPeerID}"
        )
    }
    
    /**
     * Clear all mesh service related data
     */
    private fun clearAllMeshServiceData() {
        try {
            // Request mesh service to clear all its internal data
            mesh.clearAllInternalData()
            
            Log.d(TAG, "✅ Cleared all mesh service data")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing mesh service data: ${e.message}")
        }
    }
    
    /**
     * Clear all cryptographic data including persistent identity
     */
    private fun clearAllCryptographicData() {
        try {
            // Clear encryption service persistent identity (Ed25519 signing keys)
            mesh.clearAllEncryptionData()
            
            // Clear secure identity state (if used)
            try {
                val identityManager = SecureIdentityStateManager(getApplication())
                identityManager.clearIdentityData()
                Log.d(TAG, "Cleared secure identity state")
            } catch (e: Exception) {
                Log.d(TAG, "SecureIdentityStateManager not available or already cleared: ${e.message}")
            }

            
            Log.d(TAG, "✅ Cleared all cryptographic data")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error clearing cryptographic data: ${e.message}")
        }
    }


    // Navigation Management
    
    fun showAppInfo() {
        state.setShowAppInfo(true)
    }
    
    fun hideAppInfo() {
        state.setShowAppInfo(false)
    }

    /**
     * Handle Android back navigation
     * Returns true if the back press was handled, false if it should be passed to the system
     */
    fun handleBackPressed(): Boolean {
        return when {
            // Close app info dialog
            state.getShowAppInfoValue() -> {
                hideAppInfo()
                true
            }
            // Close password dialog
            state.getShowPasswordPromptValue() -> {
                state.setShowPasswordPrompt(false)
                state.setPasswordPromptChannel(null)
                true
            }
            // Exit private chat
            state.getSelectedPrivateChatPeerValue() != null || state.getPrivateChatSheetPeerValue() != null -> {
                endPrivateChat()
                true
            }
            // Exit channel view
            state.getCurrentChannelValue() != null -> {
                switchToChannel(null)
                true
            }
            // No special navigation state - let system handle (usually exits app)
            else -> false
        }
    }


}
