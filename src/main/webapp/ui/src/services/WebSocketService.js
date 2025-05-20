import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

class WebSocketService {
  constructor() {
    this.stompClient = null;
    this.subscribers = new Map();
  }

  connect(userId) {
    const socket = new SockJS('/ws');
    this.stompClient = new Client({
      webSocketFactory: () => socket,
      debug: (str) => {
        console.log(str);
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.stompClient.onConnect = () => {
      console.log('Connected to WebSocket');
      this.subscribeToNotifications(userId);
    };

    this.stompClient.onStompError = (frame) => {
      console.error('STOMP error:', frame);
    };

    this.stompClient.activate();
  }

  subscribeToNotifications(userId) {
    if (this.stompClient && this.stompClient.connected) {
      this.stompClient.subscribe(`/topic/notifications/${userId}`, (message) => {
        const notification = JSON.parse(message.body);
        this.notifySubscribers(notification);
      });
    }
  }

  addSubscriber(callback) {
    const id = Date.now();
    this.subscribers.set(id, callback);
    return id;
  }

  removeSubscriber(id) {
    this.subscribers.delete(id);
  }

  notifySubscribers(notification) {
    this.subscribers.forEach((callback) => callback(notification));
  }

  disconnect() {
    if (this.stompClient) {
      this.stompClient.deactivate();
    }
  }
}

export const webSocketService = new WebSocketService(); 