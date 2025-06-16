import { useEffect, useRef, useState, useCallback } from "react";
import SockJS from "sockjs-client";
import { Client, IMessage } from "@stomp/stompjs";

type Notification = {
  notificationCount: number;
  messageCount: number;
  specialMessageCount: number;
};

type UseWebSocketNotificationsResult = Notification & {
  connectionStatus: "connecting" | "connected" | "error" | "disconnected";
};

export default function useWebSocketNotifications(
  userId: string
): UseWebSocketNotificationsResult {
  const [lastNotification, setLastNotification] = useState<Notification | null>(
    null
  );
  const [connectionStatus, setConnectionStatus] = useState<
    "connecting" | "connected" | "error" | "disconnected"
  >("connecting");
  const clientRef = useRef<Client | null>(null);

  const handleNotification = useCallback((message: IMessage) => {
    try {
      const notification = JSON.parse(message.body) as Notification;
      setLastNotification(notification);
    } catch {
      // ignore parse errors
    }
  }, []);

  useEffect(() => {
    const socket = new SockJS("/ws");
    const client = new Client({
      webSocketFactory: () => socket,
      debug: () => {},
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    clientRef.current = client;

    client.onConnect = () => {
      setConnectionStatus("connected");
      client.subscribe(`/topic/notifications/${userId}`, handleNotification);
    };

    client.onStompError = () => {
      setConnectionStatus("error");
    };

    client.onWebSocketClose = () => {
      setConnectionStatus("disconnected");
    };

    setConnectionStatus("connecting");
    client.activate();

    return () => {
      void client.deactivate();
      clientRef.current = null;
      setConnectionStatus("disconnected");
    };
  }, [userId, handleNotification]);

  return {
    ...(lastNotification ?? {
      notificationCount: 0,
      messageCount: 0,
      specialMessageCount: 0,
    }),
    connectionStatus,
  };
}
