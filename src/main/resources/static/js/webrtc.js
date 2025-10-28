/**
 * WebRTC Implementation for Video Meeting
 *
 * What is WebRTC?
 * WebRTC (Web Real-Time Communication) is a technology that enables
 * peer-to-peer audio, video, and data sharing between browsers without plugins.
 *
 * Key Components:
 * 1. MediaStream - Captures audio/video from user's devices
 * 2. RTC Peer Connection - Manages the connection between peers
 * 3. RTC Data Channel - For sending arbitrary data (text, files, game state, etc.) peer-to-peer.
 * 4. Signaling - Exchange of connection information via WebSocket
 *  
 * - constructor(meetingCode, username, signalSender)
 * - getUserMedia()
 * - createPeerConnection(remoteSessionId, remoteName)
 * - createOffer(remoteSessionId, remoteName)
 * - handleOffer(fromSessionId, offer, fromName)
 * - handleAnswer(fromSessionId, answer)
 * - handleIceCandidate(fromSessionId, candidate)
 * - handleRemoteStream(sessionId, participantName, stream)
 * - toggleMicrophone(), toggleVideo(), startScreenShare(), stopScreenShare()
 * - removePeer(sessionId), cleanup()
 */

class WebRTCManager {
    constructor(meetingCode, username, signalSender) {
        this.meetingCode = meetingCode;
        this.username = username;
        this.signalSender = signalSender;

        // Store peer connections with metadata
        this.peerConnections = new Map(); // sessionId -> {connection, name}

        // Local media stream
        this.localStream = null;

        // Screen sharing
        this.screenStream = null;
        this.isScreenSharing = false;

        // Media state
        this.isMuted = false;
        this.isVideoEnabled = true;

        // ICE servers
        this.iceServers = [
            { urls: 'stun:stun.l.google.com:19302' },
            { urls: 'stun:stun1.l.google.com:19302' }
        ];

        this.init();
    }

    async init() {
        try {
            console.log('WebRTC Manager: Starting initialization...');
            await this.getUserMedia();
            this.setupEventListeners();
            console.log('WebRTC Manager initialized successfully');
        } catch (error) {
            console.error('Error initializing WebRTC:', error);
            this.handleError('Failed to access camera/microphone: ' + error.message);
            // Don't throw error - let the meeting continue without video
        }
    }

    //Step 1: Get Local Media
    async getUserMedia() {
        try {
            // Stop old tracks if any
            if (this.localStream) {
                this.localStream.getTracks().forEach(t => t.stop());
            }

            this.localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });

            const localVideo = document.getElementById('localVideo');
            if (localVideo) {
                localVideo.srcObject = this.localStream;
            }

            // If we already have peer connections, swap tracks to the new devices
            for (const [, peerData] of this.peerConnections) {
                const senders = peerData.connection.getSenders();
                const audio = this.localStream.getAudioTracks()[0];
                const video = this.localStream.getVideoTracks()[0];
                const audioSender = senders.find(s => s.track && s.track.kind === 'audio');
                const videoSender = senders.find(s => s.track && s.track.kind === 'video');
                if (audio && audioSender) await audioSender.replaceTrack(audio);
                if (video && videoSender) await videoSender.replaceTrack(video);
            }

            console.log('Got local media stream');
            return this.localStream;
        } catch (error) {
            console.error('Error accessing media devices:', error);
            throw error;
        }
    }

    //Step 2: Create an RTC Peer Connection with ICE Servers
    createPeerConnection(remoteSessionId, remoteName) {
        try {
            const peerConnection = new RTCPeerConnection({
                iceServers: this.iceServers
            });

            // Add local stream tracks
            if (this.localStream) {
                this.localStream.getTracks().forEach(track => {
                    peerConnection.addTrack(track, this.localStream);
                });
            }

            // Handle incoming remote stream
            peerConnection.ontrack = (event) => {
                console.log('Received remote stream from:', remoteName, 'streams:', event.streams.length);
                if (event.streams && event.streams[0]) {
                    this.handleRemoteStream(remoteSessionId, remoteName, event.streams[0]);
                } else {
                    console.warn('No stream in track event from:', remoteName);
                }
            };

            // Handle ICE candidates
            peerConnection.onicecandidate = (event) => {
                if (event.candidate) {
                    console.log('Sending ICE candidate to:', remoteName);
                    this.signalSender.sendSignal({
                        type: 'ice-candidate',
                        data: event.candidate,
                        targetSessionId: remoteSessionId
                    });
                }
            };

            // Handle connection state changes
            peerConnection.onconnectionstatechange = () => {
                console.log(`Connection state with ${remoteName}:`, peerConnection.connectionState);

                // Update global connection status based on any successful connection
                if (peerConnection.connectionState === 'connected') {
                    this.updateConnectionStatus('connected');
                } else if (peerConnection.connectionState === 'connecting') {
                    this.updateConnectionStatus('connecting');
                } else if (peerConnection.connectionState === 'disconnected' || 
                           peerConnection.connectionState === 'failed') {
                    this.handlePeerDisconnection(remoteSessionId);
                    
                    // Only show disconnected if no other peers are connected
                    const hasConnectedPeers = Array.from(this.peerConnections.values())
                        .some(peer => peer.connection.connectionState === 'connected');
                    if (!hasConnectedPeers) {
                        this.updateConnectionStatus('disconnected');
                    }
                }
            };

            // Store with metadata
            this.peerConnections.set(remoteSessionId, {
                connection: peerConnection,
                name: remoteName
            });

            return peerConnection;
        } catch (error) {
            console.error('Error creating peer connection:', error);
            return null;
        }
    }

    //Step 3: Generate an SDP Offer (Signaling Begins)
    async createOffer(remoteSessionId, remoteName) {
        try {
            console.log('📞 Creating offer for:', remoteName, remoteSessionId);
            
            // Check if we already have a connection to this peer
            if (this.peerConnections.has(remoteSessionId)) {
                console.log('⚠️ Peer connection already exists for:', remoteName);
                return;
            }

            // Ensure we have local stream before creating offer
            if (!this.localStream) {
                console.log('⏳ Waiting for local stream...');
                await this.getUserMedia();
            }
            
            const peerConnection = this.createPeerConnection(remoteSessionId, remoteName);
            if (!peerConnection) {
                console.error('❌ Failed to create peer connection for:', remoteName);
                return;
            }

            // Create offer with proper constraints
            const offer = await peerConnection.createOffer({
                offerToReceiveAudio: true,
                offerToReceiveVideo: true
            });
            
            await peerConnection.setLocalDescription(offer);

            console.log('📤 Sending offer to:', remoteName);

            this.signalSender.sendSignal({
                type: 'offer',
                data: offer,
                targetSessionId: remoteSessionId
            });
        } catch (error) {
            console.error('❌ Error creating offer for', remoteName, ':', error);
        }
    }
    
    //Step 4: Handle incoming offer and send answer
    async handleOffer(fromSessionId, offer, fromName) {
        try {
            console.log('📥 Handling offer from:', fromName || fromSessionId);
            
            // Ensure we have local stream
            if (!this.localStream) {
                console.log('⏳ Getting local stream for answer...');
                await this.getUserMedia();
            }
            
            // Get or create peer connection
            let peerConnection = this.peerConnections.get(fromSessionId)?.connection;
            if (!peerConnection) {
                console.log('🔗 Creating new peer connection for offer from:', fromName);
                peerConnection = this.createPeerConnection(fromSessionId, fromName || 'Unknown');
            } else {
                // Update the name if we have a better one
                const peerData = this.peerConnections.get(fromSessionId);
                if (peerData && fromName && fromName !== 'Unknown' && peerData.name === 'Unknown') {
                    console.log('🏷️ Updating peer name from Unknown to:', fromName);
                    peerData.name = fromName;
                }
            }
            
            if (!peerConnection) {
                console.error('❌ Failed to create peer connection for offer');
                return;
            }

            // Set remote description and create answer
            await peerConnection.setRemoteDescription(new RTCSessionDescription(offer));
            console.log('✅ Set remote description for:', fromName);
            
            // Process any queued ICE candidates
            const peerData = this.peerConnections.get(fromSessionId);
            if (peerData && peerData.queuedCandidates && peerData.queuedCandidates.length > 0) {
                console.log('🧊 Processing', peerData.queuedCandidates.length, 'queued ICE candidates for:', fromName);
                for (const candidate of peerData.queuedCandidates) {
                    try {
                        await peerConnection.addIceCandidate(new RTCIceCandidate(candidate));
                    } catch (e) {
                        console.warn('Failed to add queued ICE candidate:', e);
                    }
                }
                peerData.queuedCandidates = [];
            }
            
            const answer = await peerConnection.createAnswer();
            await peerConnection.setLocalDescription(answer);

            console.log('📤 Sending answer to:', fromName || fromSessionId);

            this.signalSender.sendSignal({
                type: 'answer',
                data: answer,
                targetSessionId: fromSessionId
            });
        } catch (error) {
            console.error('❌ Error handling offer from', fromName, ':', error);
        }
    }

    //Step 4: Handle incoming answer
    async handleAnswer(fromSessionId, answer) {
        try {
            const peerData = this.peerConnections.get(fromSessionId);
            if (peerData && peerData.connection) {
                await peerData.connection.setRemoteDescription(new RTCSessionDescription(answer));
                console.log('✅ Set remote description from answer for:', peerData.name);
                
                // Process any queued ICE candidates
                if (peerData.queuedCandidates && peerData.queuedCandidates.length > 0) {
                    console.log('🧊 Processing', peerData.queuedCandidates.length, 'queued ICE candidates for:', peerData.name);
                    for (const candidate of peerData.queuedCandidates) {
                        try {
                            await peerData.connection.addIceCandidate(new RTCIceCandidate(candidate));
                        } catch (e) {
                            console.warn('Failed to add queued ICE candidate:', e);
                        }
                    }
                    peerData.queuedCandidates = [];
                }
            }
        } catch (error) {
            console.error('❌ Error handling answer:', error);
        }
    }

    async handleIceCandidate(fromSessionId, candidate) {
        try {
            const peerData = this.peerConnections.get(fromSessionId);
            if (peerData && peerData.connection) {
                // Check if remote description is set before adding ICE candidate
                if (peerData.connection.remoteDescription) {
                    await peerData.connection.addIceCandidate(new RTCIceCandidate(candidate));
                    console.log('🧊 Added ICE candidate from:', peerData.name);
                } else {
                    console.log('⏳ Queuing ICE candidate for:', peerData.name, '(no remote description yet)');
                    // Store candidate for later if remote description isn't set yet
                    if (!peerData.queuedCandidates) {
                        peerData.queuedCandidates = [];
                    }
                    peerData.queuedCandidates.push(candidate);
                }
            } else {
                console.warn('⚠️ No peer connection found for ICE candidate from:', fromSessionId);
            }
        } catch (error) {
            console.error('❌ Error handling ICE candidate:', error);
        }
    }

    handleRemoteStream(sessionId, participantName, stream) {
        console.log('🎥 Handling remote stream for:', participantName, sessionId);
        console.log('📊 Stream info - Tracks:', stream.getTracks().length, 
                   'Video tracks:', stream.getVideoTracks().length, 
                   'Audio tracks:', stream.getAudioTracks().length);
        
        // Check if video already exists
        let videoContainer = document.getElementById(`video-container-${sessionId}`);
        
        if (videoContainer) {
            // Update existing video stream
            const video = videoContainer.querySelector('video');
            if (video) {
                if (video.srcObject !== stream) {
                    console.log('🔄 Updating existing video stream for:', participantName);
                    video.srcObject = stream;
                    
                    // Force play
                    video.play().catch(e => console.log('Play failed:', e));
                } else {
                    console.log('📺 Stream already set for:', participantName);
                }
            }
            return;
        }

        console.log('🆕 Creating new video container for:', participantName);

        // Create new video container
        videoContainer = document.createElement('div');
        videoContainer.className = 'video-container remote-video';
        videoContainer.id = `video-container-${sessionId}`;

        const video = document.createElement('video');
        video.id = `video-${sessionId}`;
        video.autoplay = true;
        video.playsinline = true;
        video.muted = false; // Remote videos should not be muted
        video.controls = false;
        
        // Set the stream immediately
        video.srcObject = stream;

        // Add comprehensive event listeners
        video.addEventListener('loadstart', () => {
            console.log('📺 Video load started for:', participantName);
        });

        video.addEventListener('loadedmetadata', () => {
            console.log('📊 Video metadata loaded for:', participantName, 
                       'Size:', video.videoWidth, 'x', video.videoHeight);
        });

        video.addEventListener('canplay', () => {
            console.log('▶️ Video can play for:', participantName);
            // Ensure it starts playing
            video.play().catch(e => console.log('Auto-play failed:', e));
        });

        video.addEventListener('playing', () => {
            console.log('✅ Video is playing for:', participantName);
        });

        video.addEventListener('pause', () => {
            console.log('⏸️ Video paused for:', participantName);
        });

        video.addEventListener('error', (e) => {
            console.error('❌ Video error for:', participantName, e.target.error);
        });

        video.addEventListener('stalled', () => {
            console.warn('⚠️ Video stalled for:', participantName);
        });

        const overlay = document.createElement('div');
        overlay.className = 'video-overlay';

        const participantNameSpan = document.createElement('span');
        participantNameSpan.className = 'participant-name';
        participantNameSpan.textContent = participantName || 'Participant';

        const controls = document.createElement('div');
        controls.className = 'video-controls';

        overlay.appendChild(participantNameSpan);
        overlay.appendChild(controls);

        videoContainer.appendChild(video);
        videoContainer.appendChild(overlay);

        // Add to video grid
        const videoGrid = document.getElementById('videoGrid');
        if (videoGrid) {
            videoGrid.appendChild(videoContainer);
            this.updateVideoGrid();
            console.log('✅ Added remote video container to grid for:', participantName, sessionId);
            
            // Try to start playing immediately
            setTimeout(() => {
                video.play().catch(e => console.log('Delayed play failed:', e));
            }, 100);
        } else {
            console.error('❌ Video grid not found!');
        }
    }

    removePeer(sessionId) {
        const peerData = this.peerConnections.get(sessionId);
        
        if (peerData && peerData.connection) {
            peerData.connection.close();
            this.peerConnections.delete(sessionId);
            console.log('Closed peer connection for:', peerData.name);
        }

        // Remove video element
        const videoContainer = document.getElementById(`video-container-${sessionId}`);
        if (videoContainer) {
            videoContainer.remove();
            this.updateVideoGrid();
            console.log('Removed video for:', sessionId);
        }
    }

    toggleMicrophone() {
        if (this.localStream) {
            const audioTrack = this.localStream.getAudioTracks()[0];
            if (audioTrack) {
                audioTrack.enabled = !audioTrack.enabled;
                this.isMuted = !audioTrack.enabled;

                const micBtn = document.getElementById('micBtn');
                const muteIndicator = document.getElementById('localMuteIndicator');

                if (this.isMuted) {
                    micBtn.classList.remove('btn-success');
                    micBtn.classList.add('btn-danger', 'muted');
                    micBtn.innerHTML = '<i class="fas fa-microphone-slash"></i>';
                    if (muteIndicator) muteIndicator.style.display = 'block';
                } else {
                    micBtn.classList.remove('btn-danger', 'muted');
                    micBtn.classList.add('btn-success');
                    micBtn.innerHTML = '<i class="fas fa-microphone"></i>';
                    if (muteIndicator) muteIndicator.style.display = 'none';
                }

                console.log('Microphone toggled:', this.isMuted ? 'muted' : 'unmuted');
                return !this.isMuted;
            }
        }
        return false;
    }

    toggleVideo() {
        if (this.localStream) {
            const videoTrack = this.localStream.getVideoTracks()[0];
            if (videoTrack) {
                videoTrack.enabled = !videoTrack.enabled;
                this.isVideoEnabled = videoTrack.enabled;

                const videoBtn = document.getElementById('videoBtn');

                if (!this.isVideoEnabled) {
                    videoBtn.classList.remove('btn-success');
                    videoBtn.classList.add('btn-danger');
                    videoBtn.innerHTML = '<i class="fas fa-video-slash"></i>';
                    this.showVideoPlaceholder('localVideo', this.username);
                } else {
                    videoBtn.classList.remove('btn-danger');
                    videoBtn.classList.add('btn-success');
                    videoBtn.innerHTML = '<i class="fas fa-video"></i>';
                    this.hideVideoPlaceholder('localVideo');
                }

                console.log('Video toggled:', this.isVideoEnabled ? 'enabled' : 'disabled');
                return this.isVideoEnabled;
            }
        }
        return false;
    }

    async startScreenShare() {
        try {
            this.screenStream = await navigator.mediaDevices.getDisplayMedia({
                video: true,
                audio: true
            });

            const videoTrack = this.screenStream.getVideoTracks()[0];

            // Replace video track in all peer connections
            for (const [sessionId, peerData] of this.peerConnections) {
                const sender = peerData.connection.getSenders().find(s =>
                    s.track && s.track.kind === 'video'
                );

                if (sender) {
                    await sender.replaceTrack(videoTrack);
                }
            }

            // Update local video
            const localVideo = document.getElementById('localVideo');
            if (localVideo) {
                localVideo.srcObject = this.screenStream;
            }

            const localContainer = document.querySelector('.local-video');
            if (localContainer) {
                localContainer.classList.add('screen-sharing');
            }

            // Handle screen share end
            videoTrack.onended = () => {
                this.stopScreenShare();
            };

            this.isScreenSharing = true;

            const screenBtn = document.getElementById('screenShareBtn');
            screenBtn.classList.remove('btn-secondary');
            screenBtn.classList.add('btn-warning');
            screenBtn.innerHTML = '<i class="fas fa-stop"></i>';

            console.log('Screen sharing started');
            return true;
        } catch (error) {
            console.error('Error starting screen share:', error);
            return false;
        }
    }

    async stopScreenShare() {
        try {
            if (this.screenStream) {
                this.screenStream.getTracks().forEach(track => track.stop());
                this.screenStream = null;
            }

            // Replace with camera stream
            const videoTrack = this.localStream.getVideoTracks()[0];

            for (const [sessionId, peerData] of this.peerConnections) {
                const sender = peerData.connection.getSenders().find(s =>
                    s.track && s.track.kind === 'video'
                );

                if (sender && videoTrack) {
                    await sender.replaceTrack(videoTrack);
                }
            }

            // Update local video
            const localVideo = document.getElementById('localVideo');
            if (localVideo) {
                localVideo.srcObject = this.localStream;
            }

            const localContainer = document.querySelector('.local-video');
            if (localContainer) {
                localContainer.classList.remove('screen-sharing');
            }

            this.isScreenSharing = false;

            const screenBtn = document.getElementById('screenShareBtn');
            screenBtn.classList.remove('btn-warning');
            screenBtn.classList.add('btn-secondary');
            screenBtn.innerHTML = '<i class="fas fa-desktop"></i>';

            console.log('Screen sharing stopped');
        } catch (error) {
            console.error('Error stopping screen share:', error);
        }
    }

    updateVideoGrid() {
        const videoGrid = document.getElementById('videoGrid');
        if (!videoGrid) return;

        const videoContainers = videoGrid.querySelectorAll('.video-container');
        const count = videoContainers.length;

        videoGrid.className = 'video-grid';
        videoGrid.classList.add(`participants-${Math.min(count, 6)}`);

        console.log('Updated video grid for', count, 'participants');
    }

    showVideoPlaceholder(videoId, name) {
        const video = document.getElementById(videoId);
        if (video && video.parentNode) {
            let placeholder = video.parentNode.querySelector('.video-placeholder');
            
            if (!placeholder) {
                placeholder = document.createElement('div');
                placeholder.className = 'video-placeholder';
                placeholder.innerHTML = `
                    <i class="fas fa-user-circle"></i>
                    <div class="name">${name}</div>
                `;
//                video.style.display = 'none';
                video.parentNode.insertBefore(placeholder, video);
            }
        }
    }

    hideVideoPlaceholder(videoId) {
        const video = document.getElementById(videoId);
        if (video && video.parentNode) {
            const placeholder = video.parentNode.querySelector('.video-placeholder');
            if (placeholder) {
                placeholder.remove();
            }
            video.style.display = 'block';
        }
    }

    handlePeerDisconnection(sessionId) {
        console.log('Peer disconnected:', sessionId);
        this.removePeer(sessionId);
    }

    updateConnectionStatus(status) {
        const statusElement = document.getElementById('connectionStatus');
        if (statusElement && statusElement.dataset.currentStatus !== status) {
            statusElement.dataset.currentStatus = status;

            switch (status) {
                case 'connected':
                    statusElement.innerHTML = '<i class="fas fa-circle text-success"></i> Connected';
                    break;
                case 'connecting':
                    statusElement.innerHTML = '<i class="fas fa-circle text-warning"></i> Connecting';
                    break;
                case 'disconnected':
                    statusElement.innerHTML = '<i class="fas fa-circle text-danger"></i> Disconnected';
                    break;
            }
        }
    }

    handleError(message) {
        console.error('WebRTC Error:', message);

        const alert = document.createElement('div');
        alert.className = 'alert alert-danger alert-dismissible fade show position-fixed';
        alert.style.top = '80px';
        alert.style.right = '20px';
        alert.style.zIndex = '9999';
        alert.innerHTML = `
            <i class="fas fa-exclamation-circle"></i> ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;

        document.body.appendChild(alert);

        setTimeout(() => {
            if (alert.parentNode) {
                alert.remove();
            }
        }, 5000);
    }

    setupEventListeners() {
        window.addEventListener('beforeunload', () => {
            this.cleanup();
        });

        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                console.log('Page hidden - preserving connections');
            } else {
                console.log('Page visible - resuming');
            }
        });
    }

    // Method to update participant name if we get better information
    updateParticipantName(sessionId, newName) {
        const peerData = this.peerConnections.get(sessionId);
        if (peerData && newName && newName !== 'Unknown') {
            console.log('🏷️ Updating participant name for', sessionId, 'from', peerData.name, 'to', newName);
            peerData.name = newName;
            
            // Update the video overlay as well
            const videoContainer = document.getElementById(`video-container-${sessionId}`);
            if (videoContainer) {
                const nameSpan = videoContainer.querySelector('.participant-name');
                if (nameSpan) {
                    nameSpan.textContent = newName;
                }
            }
        }
    }

    cleanup() {
        console.log('Cleaning up WebRTC resources');

        for (const [sessionId, peerData] of this.peerConnections) {
            if (peerData.connection) {
                peerData.connection.close();
            }
        }
        this.peerConnections.clear();

        if (this.localStream) {
            this.localStream.getTracks().forEach(track => track.stop());
        }

        if (this.screenStream) {
            this.screenStream.getTracks().forEach(track => track.stop());
        }
    }
}

window.WebRTCManager = WebRTCManager;