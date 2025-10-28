class ChatManager {
	constructor(meetingCode, username, stompClient) {
		this.meetingCode = meetingCode;
		this.username = username;
		this.stompClient = stompClient;

		this.isChatOpen = false;
		this.unreadMessages = 0;

		this.setupWebSocketSubscription();
		this.exposeUIHandlers();
	}

	setupWebSocketSubscription() {
		if (!this.stompClient) return;
		this.stompClient.subscribe(`/topic/meeting/${this.meetingCode}/chat`, (message) => {
			const data = JSON.parse(message.body);
			this.handleChatMessage(data);
		});
	}

	exposeUIHandlers() {
		window.toggleChat = () => {
			this.toggleChat();
		};

		window.sendChatMessage = () => {
			this.sendChatMessage();
		};

		window.handleChatKeyPress = (event) => {
			if (event.key === 'Enter') {
				this.sendChatMessage();
			}
		};
	}

	toggleChat() {
		const chatSidebar = document.getElementById('chatSidebar');
		this.isChatOpen = !this.isChatOpen;

		if (this.isChatOpen) {
			chatSidebar.classList.add('show');
			this.unreadMessages = 0;
			this.updateChatNotification();
		} else {
			chatSidebar.classList.remove('show');
		}
	}

	sendChatMessage() {
		const chatInput = document.getElementById('chatInput');
		const message = chatInput.value.trim();

		if (message && this.stompClient && this.stompClient.connected) {
			const chatData = {
				message: message,
				senderName: this.username
			};

			this.stompClient.send(`/app/meeting/${this.meetingCode}/chat`, {}, JSON.stringify(chatData));
			chatInput.value = '';
		}
	}

	handleChatMessage(data) {
		if (data.type === 'CHAT_MESSAGE') {
			const isOwn = data.senderName === this.username;
			this.addChatMessage(data.senderName, data.message, data.timestamp, isOwn);
			if (!this.isChatOpen && !isOwn) {
				this.unreadMessages++;
				this.updateChatNotification();
			}
		}
	}

	addChatMessage(sender, message, timestamp, isOwn = false) {
		const chatMessages = document.getElementById('chatMessages');

		const placeholder = chatMessages.querySelector('.text-muted');
		if (placeholder) {
			placeholder.remove();
		}

		const messageDiv = document.createElement('div');
		messageDiv.className = `chat-message ${isOwn ? 'own' : ''}`;

		const time = new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

		messageDiv.innerHTML = `
			<div><strong>${sender}</strong> <small class="text-muted">${time}</small></div>
			<div>${message}</div>
		`;

		chatMessages.appendChild(messageDiv);
		chatMessages.scrollTop = chatMessages.scrollHeight;
	}

	addSystemMessage(message) {
		const chatMessages = document.getElementById('chatMessages');

		const placeholder = chatMessages.querySelector('.text-muted');
		if (placeholder) {
			placeholder.remove();
		}

		const messageDiv = document.createElement('div');
		messageDiv.className = 'chat-message system';
		messageDiv.innerHTML = `<em>${message}</em>`;

		chatMessages.appendChild(messageDiv);
		chatMessages.scrollTop = chatMessages.scrollHeight;
	}

	updateChatNotification() {
		const notification = document.getElementById('chatNotification');
		if (this.unreadMessages > 0) {
			notification.textContent = this.unreadMessages;
			notification.style.display = 'flex';
		} else {
			notification.style.display = 'none';
		}
	}
}

window.ChatManager = ChatManager;


