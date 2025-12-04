class ChatManager {
	constructor(meetingCode, username, stompClient) {
		this.meetingCode = meetingCode;
		this.username = username;
		this.stompClient = stompClient;

		this.isChatOpen = false;
		this.unreadMessages = 0;

		this.setupWebSocketSubscription();
	}

	setupWebSocketSubscription() {
		if (!this.stompClient) return;
		
		// 4. Chat Messages (Send and receive chat messages)
		this.stompClient.subscribe(`/topic/meeting/${this.meetingCode}/chat`, (message) => {
			const data = JSON.parse(message.body);
			this.handleChatMessage(data);
		});
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

		if (!message) return;
		if (!this.stompClient || !this.stompClient.connected) return;

		const chatData = {
			message: message,
			senderName: this.username
		};

		this.stompClient.send(`/app/meeting/${this.meetingCode}/chat`, {}, JSON.stringify(chatData));
		chatInput.value = '';
	}

	handleChatMessage(data) {
		if (data.type !== 'CHAT_MESSAGE') return;

		const isOwn = data.senderName === this.username;
		this.addChatMessage(data.senderName, data.message, data.timestamp, isOwn);

		// Increment unread count if chat is closed and message is from someone else
		if (!this.isChatOpen && !isOwn) {
			this.unreadMessages++;
			this.updateChatNotification();
		}
	}

	addChatMessage(sender, message, timestamp, isOwn = false) {
		const chatMessages = document.getElementById('chatMessages');

		// Remove placeholder text if it exists
		const placeholder = chatMessages.querySelector('.text-muted');
		if (placeholder) {
			placeholder.remove();
		}

		// Create message element
		const messageDiv = document.createElement('div');
		messageDiv.className = `chat-message ${isOwn ? 'own' : ''}`;

		const time = new Date(timestamp).toLocaleTimeString([], { 
			hour: '2-digit', 
			minute: '2-digit' 
		});

		messageDiv.innerHTML = `
			<div><strong>${sender}</strong> <small class="text-muted">${time}</small></div>
			<div>${message}</div>
		`;

		chatMessages.appendChild(messageDiv);
		chatMessages.scrollTop = chatMessages.scrollHeight;
	}

	addSystemMessage(message) {
		const chatMessages = document.getElementById('chatMessages');

		// Remove placeholder text if it exists
		const placeholder = chatMessages.querySelector('.text-muted');
		if (placeholder) {
			placeholder.remove();
		}

		// Create system message element
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


