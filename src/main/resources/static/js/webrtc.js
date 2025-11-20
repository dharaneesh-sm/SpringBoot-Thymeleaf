/**
 * WebRTC Implementation for Video Meeting
 *
 * Key Components:
 * 1. MediaStream - Captures audio/video from user's devices
 * 2. RTC Peer Connection - Manages the connection between peers
 * 3. RTC Data Channel - For sending arbitrary data (text, files, state, etc.) peer-to-peer.
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
            await this.getUserMedia();
            this.setupEventListeners();
        } 
        catch (error) {
            console.error('Error initializing WebRTC:', error);
            this.handleError('Failed to access camera/microphone: ' + error.message);
        }
    }

    //Step 1: Get Local Media
    async getUserMedia() {
        try {
            // Stop existing tracks before requesting new ones
            if (this.localStream) {
                this.localStream.getTracks().forEach(track => track.stop());
            }

            // Request camera and microphone access
            this.localStream = await navigator.mediaDevices.getUserMedia({ 
                video: true, 
                audio: true 
            });

            // Display local video
            const localVideo = document.getElementById('localVideo');
            if (localVideo) {
                localVideo.srcObject = this.localStream;
            }

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
                if (event.streams && event.streams[0]) {
                    this.handleRemoteStream(remoteSessionId, remoteName, event.streams[0]);
                }
            };

            // Handle ICE candidates
            peerConnection.onicecandidate = (event) => {
                if (event.candidate) {
                    this.signalSender.sendSignal({
                        type: 'ice-candidate',
                        data: event.candidate,
                        targetSessionId: remoteSessionId
                    });
                }
            };

            // Handle connection state changes
            peerConnection.onconnectionstatechange = () => {
                if (peerConnection.connectionState === 'connected') {
                    this.updateConnectionStatus('connected');
                } else if (peerConnection.connectionState === 'connecting') {
                    this.updateConnectionStatus('connecting');
                } else if (peerConnection.connectionState === 'disconnected' ||
                    peerConnection.connectionState === 'failed') {
                    this.handlePeerDisconnection(remoteSessionId);

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
            if (this.peerConnections.has(remoteSessionId)) {
                return;
            }

            if (!this.localStream) {
                await this.getUserMedia();
            }

            const peerConnection = this.createPeerConnection(remoteSessionId, remoteName);
            if (!peerConnection) {
                console.error('Failed to create peer connection for:', remoteName);
                return;
            }

            const offer = await peerConnection.createOffer({
                offerToReceiveAudio: true,
                offerToReceiveVideo: true
            });

            await peerConnection.setLocalDescription(offer);

            this.signalSender.sendSignal({
                type: 'offer',
                data: offer,
                targetSessionId: remoteSessionId
            });
        } catch (error) {
            console.error('Error creating offer for', remoteName, ':', error);
        }
    }

    //Step 4: Handle incoming offer and send answer
    async handleOffer(fromSessionId, offer, fromName) {
        try {
            if (!this.localStream) {
                await this.getUserMedia();
            }

            let peerConnection = this.peerConnections.get(fromSessionId)?.connection;
            if (!peerConnection) {
                peerConnection = this.createPeerConnection(fromSessionId, fromName || 'Unknown');
            } else {
                const peerData = this.peerConnections.get(fromSessionId);
                if (peerData && fromName && fromName !== 'Unknown' && peerData.name === 'Unknown') {
                    peerData.name = fromName;
                }
            }

            if (!peerConnection) {
                console.error('Failed to create peer connection for offer');
                return;
            }

            await peerConnection.setRemoteDescription(new RTCSessionDescription(offer));

            // Process any queued ICE candidates
            const peerData = this.peerConnections.get(fromSessionId);
            if (peerData && peerData.queuedCandidates && peerData.queuedCandidates.length > 0) {
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

            this.signalSender.sendSignal({
                type: 'answer',
                data: answer,
                targetSessionId: fromSessionId
            });
        } catch (error) {
            console.error('Error handling offer from', fromName, ':', error);
        }
    }

    //Step 4: Handle incoming answer
    async handleAnswer(fromSessionId, answer) {
        try {
            const peerData = this.peerConnections.get(fromSessionId);
            if (peerData && peerData.connection) {
                await peerData.connection.setRemoteDescription(new RTCSessionDescription(answer));

                // Process any queued ICE candidates
                if (peerData.queuedCandidates && peerData.queuedCandidates.length > 0) {
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
            console.error('Error handling answer:', error);
        }
    }

    async handleIceCandidate(fromSessionId, candidate) {
        try {
            const peerData = this.peerConnections.get(fromSessionId);
            if (peerData && peerData.connection) {
                if (peerData.connection.remoteDescription) {
                    await peerData.connection.addIceCandidate(new RTCIceCandidate(candidate));
                } else {
                    if (!peerData.queuedCandidates) {
                        peerData.queuedCandidates = [];
                    }
                    peerData.queuedCandidates.push(candidate);
                }
            }
        } catch (error) {
            console.error('Error handling ICE candidate:', error);
        }
    }

    handleRemoteStream(sessionId, participantName, stream) {
        let videoContainer = document.getElementById(`video-container-${sessionId}`);

        if (videoContainer) {
            const video = videoContainer.querySelector('video');
            if (video && video.srcObject !== stream) {
                video.srcObject = stream;

                video.addEventListener('loadedmetadata', () => {
                    if (video.videoWidth && video.videoHeight) {
                        const aspectRatio = video.videoWidth / video.videoHeight;
                        if (aspectRatio > 1.7 || video.videoWidth >= 1280) {
                            videoContainer.classList.add('screen-sharing');
                        } else {
                            videoContainer.classList.remove('screen-sharing');
                        }
                    }
                }, { once: true });

                video.play().catch(e => console.error('Video play failed:', e));
            }
            return;
        }

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

        this.monitorVideoTrackState(stream, sessionId, participantName);

        video.addEventListener('loadedmetadata', () => {
            if (video.videoWidth && video.videoHeight) {
                const aspectRatio = video.videoWidth / video.videoHeight;
                if (aspectRatio > 1.7 || video.videoWidth >= 1280) {
                    videoContainer.classList.add('screen-sharing');
                } else {
                    videoContainer.classList.remove('screen-sharing');
                }
            }
            this.updateVideoPlaceholder(sessionId, participantName);
        });

        video.addEventListener('canplay', () => {
            video.play().catch(e => console.error('Video play failed:', e));
        });

        video.addEventListener('playing', () => {
            this.updateVideoPlaceholder(sessionId, participantName);
        });

        video.addEventListener('error', (e) => {
            console.error('Video error for:', participantName, e.target.error);
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

        const videoGrid = document.getElementById('videoGrid');
        if (videoGrid) {
            videoGrid.appendChild(videoContainer);
            this.updateVideoGrid();

            setTimeout(() => {
                video.play().catch(e => console.error('Delayed video play failed:', e));
            }, 100);
        } else {
            console.error('Video grid not found!');
        }
    }

    removePeer(sessionId) {
        const peerData = this.peerConnections.get(sessionId);

        if (peerData && peerData.connection) {
            peerData.connection.close();
            this.peerConnections.delete(sessionId);
        }

        if (this.trackMonitors && this.trackMonitors.has(sessionId)) {
            clearInterval(this.trackMonitors.get(sessionId));
            this.trackMonitors.delete(sessionId);
        }

        const videoContainer = document.getElementById(`video-container-${sessionId}`);
        if (videoContainer) {
            videoContainer.remove();
            this.updateVideoGrid();
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
    }

    // Method to update participant name if we get better information
    updateParticipantName(sessionId, newName) {
        const peerData = this.peerConnections.get(sessionId);
        if (peerData && newName && newName !== 'Unknown') {
            peerData.name = newName;

            const videoContainer = document.getElementById(`video-container-${sessionId}`);
            if (videoContainer) {
                const nameSpan = videoContainer.querySelector('.participant-name');
                if (nameSpan) {
                    nameSpan.textContent = newName;
                }

                const placeholder = videoContainer.querySelector('.video-placeholder .name');
                if (placeholder) {
                    placeholder.textContent = newName;
                }
            }
        }
    }

    // Monitor video track state changes
    monitorVideoTrackState(stream, sessionId, participantName) {
        const videoTracks = stream.getVideoTracks();

        videoTracks.forEach(track => {
            track.addEventListener('ended', () => {
                this.showRemoteVideoPlaceholder(sessionId, participantName);
            });

            // Monitor track enabled state changes
            const checkTrackState = () => {
                if (track.enabled) {
                    this.hideRemoteVideoPlaceholder(sessionId);
                } else {
                    this.showRemoteVideoPlaceholder(sessionId, participantName);
                }
            };

            // Check initial state
            setTimeout(checkTrackState, 100);

            // Set up periodic checking for track state changes
            // Note: There's no direct event for track.enabled changes, so we poll
            const trackMonitor = setInterval(() => {
                if (track.readyState === 'ended') {
                    clearInterval(trackMonitor);
                    return;
                }
                checkTrackState();
            }, 1000);

            // Store monitor reference for cleanup
            if (!this.trackMonitors) {
                this.trackMonitors = new Map();
            }
            this.trackMonitors.set(sessionId, trackMonitor);
        });
    }

    // Update video placeholder based on current track state
    updateVideoPlaceholder(sessionId, participantName) {
        const video = document.getElementById(`video-${sessionId}`);
        if (video && video.srcObject) {
            const videoTracks = video.srcObject.getVideoTracks();
            const hasEnabledVideo = videoTracks.some(track => track.enabled && track.readyState === 'live');

            if (hasEnabledVideo) {
                this.hideRemoteVideoPlaceholder(sessionId);
            } else {
                this.showRemoteVideoPlaceholder(sessionId, participantName);
            }
        }
    }

    // Show placeholder for remote participant when video is disabled
    showRemoteVideoPlaceholder(sessionId, participantName) {
        const videoContainer = document.getElementById(`video-container-${sessionId}`);
        if (!videoContainer) return;

        let placeholder = videoContainer.querySelector('.video-placeholder');
        if (placeholder) return;

        placeholder = document.createElement('div');
        placeholder.className = 'video-placeholder';
        placeholder.innerHTML = `
            <i class="fas fa-user-circle"></i>
            <div class="name">${participantName || 'Participant'}</div>
        `;

        // Insert placeholder before video element
        const video = videoContainer.querySelector('video');
        if (video) {
            videoContainer.insertBefore(placeholder, video);
        } else {
            videoContainer.appendChild(placeholder);
        }
    }

    // Hide placeholder for remote participant when video is enabled
    hideRemoteVideoPlaceholder(sessionId) {
        const videoContainer = document.getElementById(`video-container-${sessionId}`);
        if (!videoContainer) return;

        const placeholder = videoContainer.querySelector('.video-placeholder');
        if (placeholder) {
            placeholder.remove();
        }
    }

    cleanup() {
        if (this.trackMonitors) {
            for (const [sessionId, monitor] of this.trackMonitors) {
                clearInterval(monitor);
            }
            this.trackMonitors.clear();
        }

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