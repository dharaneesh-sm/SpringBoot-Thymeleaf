// - Coordinates WebSocket signaling
// - Manages participant lifecycle
// - Bridges WebRTC and WebSocket communication
// - Handles UI updates

class MeetingManager {
    constructor() {
        this.meetingCode = window.meetingData.meetingCode;
        this.username = window.meetingData.username;
        this.isHost = window.meetingData.isHost;

        // WebSocket connection
        this.socket = null;
        this.stompClient = null;

        // WebRTC manager
        this.webrtcManager = null;

        // Chat manager
        this.chatManager = null;

        // UI state
        this.isChatOpen = false;
        this.isParticipantsOpen = false;
        this.unreadMessages = 0;

        // Participants list
        this.participants = new Map(); // sessionId -> participant data

        // Initialize
        this.init();
    }

    async init() {
        try {
            this.showLoading('Connecting to meeting...');
            await this.connectWebSocket();

            this.webrtcManager = new WebRTCManager(
                this.meetingCode,
                this.username,
                this
            );

            if (window.ChatManager) {
                this.chatManager = new ChatManager(this.meetingCode, this.username, this.stompClient);
            }

            this.setupUIEventListeners();
            this.joinMeeting();
            this.hideLoading();
        } catch (error) {
            console.error('Error initializing meeting manager:', error);
            this.handleError('Failed to connect to meeting: ' + error.message);
            this.hideLoading();
        }
    }

    connectWebSocket() {
        return new Promise((resolve, reject) => {
            try {
                this.socket = new SockJS('/ws');
                this.stompClient = Stomp.over(this.socket);
                this.stompClient.debug = null;

                this.stompClient.connect({},
                    (frame) => {
                        this.setupWebSocketSubscriptions();
                        resolve();
                    },
                    (error) => {
                        console.error('WebSocket connection error:', error);
                        reject(error);
                    }
                );
            } catch (error) {
                reject(error);
            }
        });
    }

    setupWebSocketSubscriptions() {

        // Subscribe to participant updates
        // Response from handleParticipantJoin() and handleParticipantLeave()
        this.stompClient.subscribe(`/topic/meeting/${this.meetingCode}/participants`,
            (message) => {
                const data = JSON.parse(message.body);
                this.handleParticipantUpdate(data);
            }
        );

        // Subscribe to WebRTC signaling
        this.stompClient.subscribe(`/topic/meeting/${this.meetingCode}/webrtc-signal`,
            (message) => {
                const data = JSON.parse(message.body);
                this.handleWebRTCSignal(data);
            }
        );

        // Subscribe to media state changes
        // Response from handleMediaStateChange()
        this.stompClient.subscribe(`/topic/meeting/${this.meetingCode}/media-state`,
            (message) => {
                const data = JSON.parse(message.body);
                this.handleMediaStateChange(data);
            }
        );

        // Subscribe to control messages (e.g., meeting ended)
        this.stompClient.subscribe(`/topic/meeting/${this.meetingCode}/control`,
            (message) => {
                const data = JSON.parse(message.body);
                if (data.type === 'MEETING_ENDED') {
                    if (this.chatManager) {
                        this.chatManager.addSystemMessage(`Meeting ended by ${data.endedBy}`);
                    }
                    this.cleanup();
                    window.location.href = '/';
                }
            }
        );
    }

    joinMeeting() {
        const joinMessage = {
            participantName: this.username
        };

        // Calls handleParticipantJoin()
        this.stompClient.send(`/app/meeting/${this.meetingCode}/join`,{}, JSON.stringify(joinMessage));
    }

    handleParticipantUpdate(data) {
        if (data.type === 'PARTICIPANT_JOINED') {
            const participant = data.participant;
            this.participants.set(participant.sessionId, participant);

            if (participant.name === this.username) {
                this.sessionId = participant.sessionId;

                // Create offers to existing participants
                setTimeout(() => {
                    if (Array.isArray(data.participants)) {
                        const existingParticipants = data.participants.filter(p => p.name !== this.username);

                        existingParticipants.forEach((p, index) => {
                            setTimeout(() => {
                                this.webrtcManager.createOffer(p.sessionId, p.name);
                            }, index * 300);
                        });
                    }
                }, 1000);
            } else {
                if (this.webrtcManager && this.sessionId) {
                    setTimeout(() => {
                        this.webrtcManager.createOffer(participant.sessionId, participant.name);
                    }, 500);
                }

                if (this.chatManager) {
                    this.chatManager.addSystemMessage(`${participant.name} joined the meeting`);
                }
            }

        } else if (data.type === 'PARTICIPANT_LEFT') {
            const sessionId = data.sessionId;
            const participant = this.participants.get(sessionId);

            if (participant) {
                this.participants.delete(sessionId);
                if (this.webrtcManager) {
                    this.webrtcManager.removePeer(sessionId);
                }
                if (this.chatManager) {
                    this.chatManager.addSystemMessage(`${participant.name} left the meeting`);
                }
            }
        }

        // Update UI
        if (data.participantCount !== undefined) {
            this.updateParticipantCount(data.participantCount);
        }

        if (data.participants) {
            // Update participants map with full list
            data.participants.forEach(p => {
                if (p.sessionId && p.name) {
                    this.participants.set(p.sessionId, p);
                }
            });

            this.updateParticipantsList(data.participants);

            // Update WebRTC participant names if we have better information
            if (this.webrtcManager) {
                data.participants.forEach(p => {
                    if (p.sessionId && p.name) {
                        this.webrtcManager.updateParticipantName(p.sessionId, p.name);
                    }
                });
            }
        }
    }

    handleWebRTCSignal(data) {
        const { type, fromSessionId, targetSessionId } = data;

        // Ignore messages not intended for us or our own messages
        if (targetSessionId && targetSessionId !== "" && this.sessionId && targetSessionId !== this.sessionId) {
            return;
        }

        if (this.sessionId && fromSessionId === this.sessionId) {
            return;
        }

        // Get participant name
        let fromName = 'Unknown';
        const participant = this.participants.get(fromSessionId);
        if (participant && participant.name) {
            fromName = participant.name;
        } else if (this.webrtcManager) {
            const peerData = this.webrtcManager.peerConnections.get(fromSessionId);
            if (peerData && peerData.name) {
                fromName = peerData.name;
            }
        }

        // Process the signal
        if (this.webrtcManager) {
            switch (type) {
                case 'offer':
                    this.webrtcManager.handleOffer(fromSessionId, data.data, fromName);
                    break;
                case 'answer':
                    this.webrtcManager.handleAnswer(fromSessionId, data.data);
                    break;
                case 'ice-candidate':
                    this.webrtcManager.handleIceCandidate(fromSessionId, data.data);
                    break;
            }
        }
    }

    sendSignal(signalData) {
        if (this.stompClient && this.stompClient.connected) {

            // Calls handleWebRTCSignaling()
            this.stompClient.send(`/app/meeting/${this.meetingCode}/webrtc-signal`,{}, JSON.stringify(signalData));
        }
    }

    handleMediaStateChange(data) {
        if (data.type === 'MEDIA_STATE_CHANGED') {
            const { sessionId, participantName, isMuted, videoEnabled } = data;
            console.log('📡 Media state change:', { sessionId, participantName, isMuted, videoEnabled, mySessionId: this.sessionId });

            // Update participant in our list
            const participant = this.participants.get(sessionId);
            if (participant) {
                participant.isMuted = isMuted;
                participant.videoEnabled = videoEnabled;
            }

            // Update video overlay
            this.updateParticipantVideoOverlay(sessionId, { isMuted, videoEnabled });

            // Handle video placeholder for remote participants
            if (this.webrtcManager && sessionId !== this.sessionId) {
                console.log('🎬 Handling remote video placeholder:', videoEnabled ? 'HIDE' : 'SHOW');
                if (videoEnabled) {
                    this.webrtcManager.hideRemoteVideoPlaceholder(sessionId);
                } else {
                    this.webrtcManager.showRemoteVideoPlaceholder(sessionId, participantName);
                }
            } else {
                console.log('ℹ️ Skipping placeholder (own video or no webrtcManager)');
            }
        }
    }

    updateParticipantCount(count) {
        const elements = ['participantCount', 'modalParticipantCount'];
        elements.forEach(id => {
            const element = document.getElementById(id);
            if (element) {
                element.textContent = count;
            }
        });
    }

    updateParticipantsList(participants) {
        const participantsList = document.getElementById('participantsList');
        if (!participantsList) return;

        participantsList.innerHTML = '';

        participants.forEach(participant => {
            const participantItem = document.createElement('div');
            participantItem.className = 'participant-item';
            participantItem.innerHTML = `
                <div class="participant-info">
                    <div class="participant-name">
                        ${participant.name}
                        ${participant.isHost ? '<i class="fas fa-crown text-warning ms-1" title="Host"></i>' : ''}
                        ${participant.name === this.username ? '<span class="badge bg-primary ms-1">You</span>' : ''}
                    </div>
                    <div class="participant-status">
                        ${participant.isMuted ? '<i class="fas fa-microphone-slash text-danger"></i>' : '<i class="fas fa-microphone text-success"></i>'}
                        ${participant.videoEnabled ? '<i class="fas fa-video text-success ms-1"></i>' : '<i class="fas fa-video-slash text-danger ms-1"></i>'}
                    </div>
                </div>
            `;

            participantsList.appendChild(participantItem);
        });
    }

    updateParticipantVideoOverlay(sessionId, mediaState) {
        const videoContainer = document.getElementById(`video-container-${sessionId}`);
        if (videoContainer) {
            const controls = videoContainer.querySelector('.video-controls');
            if (controls) {
                controls.innerHTML = '';

                if (mediaState.isMuted) {
                    const muteIndicator = document.createElement('span');
                    muteIndicator.className = 'mute-indicator';
                    muteIndicator.innerHTML = '<i class="fas fa-microphone-slash"></i>';
                    controls.appendChild(muteIndicator);
                }
            }
        }
    }

    setupUIEventListeners() {
        // Setup global functions for button clicks
        window.toggleMicrophone = () => {
            const enabled = this.webrtcManager.toggleMicrophone();
            this.broadcastMediaState();
        };

        window.toggleVideo = () => {
            const enabled = this.webrtcManager.toggleVideo();
            this.broadcastMediaState();
        };

        window.toggleScreenShare = () => {
            if (this.webrtcManager.isScreenSharing) {
                this.webrtcManager.stopScreenShare();
            } else {
                this.webrtcManager.startScreenShare();
            }
        };

        // Chat handlers delegate to ChatManager when available
        window.toggleChat = () => {
            if (this.chatManager) {
                this.chatManager.toggleChat();
            }
        };

        window.sendChatMessage = () => {
            if (this.chatManager) {
                this.chatManager.sendChatMessage();
            }
        };

        window.handleChatKeyPress = (event) => {
            if (event.key === 'Enter' && this.chatManager) {
                this.chatManager.sendChatMessage();
            }
        };

        window.toggleParticipants = () => {
            this.toggleParticipants();
        };

        window.leaveMeeting = () => {
            this.leaveMeeting();
        };

        window.endMeeting = () => {
            this.endMeeting();
        };

        window.copyMeetingCode = () => {
            this.copyMeetingCode();
        };

    }

    // Broadcast media state changes to other participants
    broadcastMediaState() {
        const mediaState = {
            participantName: this.username,
            isMuted: this.webrtcManager.isMuted,
            videoEnabled: this.webrtcManager.isVideoEnabled
        };

        if (this.stompClient && this.stompClient.connected) {

            // Calls handleMediaStateChange()
            this.stompClient.send(`/app/meeting/${this.meetingCode}/media-state`,{}, JSON.stringify(mediaState));
        }
    }

    toggleParticipants() {
        const modal = new bootstrap.Modal(document.getElementById('participantsModal'));
        modal.show();
    }

    async copyMeetingCode() {
        try {
            await navigator.clipboard.writeText(this.meetingCode);
            this.showSuccess('Meeting code copied to clipboard!');
        } catch (error) {
            console.error('Error copying to clipboard:', error);

            // Fallback for older browsers
            const textArea = document.createElement('textarea');
            textArea.value = this.meetingCode;
            document.body.appendChild(textArea);
            textArea.focus();
            textArea.select();
            document.execCommand('copy');
            document.body.removeChild(textArea);

            this.showSuccess('Meeting code copied to clipboard!');
        }
    }

    leaveMeeting() {
        if (confirm('Are you sure you want to leave the meeting?')) {
            this.cleanup();
            window.location.href = '/';
        }
    }

    async endMeeting() {
        if (!this.isHost) return;

        if (confirm('Are you sure you want to end the meeting for everyone?')) {
            try {
                const response = await fetch(`/api/meetings/${this.meetingCode}/end`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded'
                    },
                    body: `username=${encodeURIComponent(this.username)}`
                });

                if (response.ok) {
                    this.cleanup();
                    window.location.href = '/';
                } else {
                    this.showError('Failed to end meeting');
                }
            } catch (error) {
                console.error('Error ending meeting:', error);
                this.showError('Failed to end meeting');
            }
        }
    }

    showLoading(message = 'Loading...') {
        const overlay = document.getElementById('loadingOverlay');
        const text = overlay.querySelector('p');
        text.textContent = message;
        overlay.style.display = 'flex';
    }

    hideLoading() {
        const overlay = document.getElementById('loadingOverlay');
        overlay.style.display = 'none';
    }

    showSuccess(message) {
        this.showAlert(message, 'success');
    }

    showError(message) {
        this.showAlert(message, 'danger');
    }

    showAlert(message, type = 'info') {
        const alert = document.createElement('div');
        alert.className = `alert alert-${type} alert-dismissible fade show position-fixed`;
        alert.style.top = '80px';
        alert.style.right = '20px';
        alert.style.zIndex = '9999';
        alert.style.minWidth = '300px';

        const iconClass = type === 'success' ? 'fa-check-circle' :
            type === 'danger' ? 'fa-exclamation-circle' : 'fa-info-circle';

        alert.innerHTML = `
            <i class="fas ${iconClass}"></i> ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;

        document.body.appendChild(alert);

        // Auto remove after 4 seconds
        setTimeout(() => {
            if (alert.parentNode) {
                alert.remove();
            }
        }, 4000);
    }

    handleError(message) {
        console.error('Meeting Error:', message);
        this.showError(message);
    }

    cleanup() {

        // Send leave message
        if (this.stompClient && this.stompClient.connected) {

            // Calls handleParticipantLeave()
            this.stompClient.send(`/app/meeting/${this.meetingCode}/leave`, {}, '{}');
        }

        // Cleanup WebRTC
        if (this.webrtcManager) {
            this.webrtcManager.cleanup();
        }

        // Disconnect WebSocket
        if (this.stompClient) {
            this.stompClient.disconnect();
        }
    }
}

// Initialize meeting when page loads
document.addEventListener('DOMContentLoaded', () => {
    // Only initialize if we're in a meeting room
    if (window.meetingData) {
        window.meetingManager = new MeetingManager();
    }
});

// Cleanup when page unloads
window.addEventListener('beforeunload', () => {
    if (window.meetingManager) {
        window.meetingManager.cleanup();
    }
});