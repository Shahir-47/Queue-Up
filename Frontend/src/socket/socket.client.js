// Frontend/src/socket/socket.client.js

// This file sets up a WebSocket client that mimics Socket.IO's API using standard WebSockets.
// It handles connection, reconnection, and event listening in a way similar to Socket.IO.
const baseUrl =
	import.meta.env.MODE === "development"
		? "localhost:8080"
		: import.meta.env.VITE_BACKEND_URL.replace(/^https?:\/\//, "");

const SOCKET_URL =
	import.meta.env.MODE === "development"
		? `ws://${baseUrl}/ws`
		: `wss://${baseUrl}/ws`;

let socket = null;
let userId = null; // Store userId to allow reconnection
let reconnectTimer = null;
const listeners = {}; // Stores event callbacks: { 'newMatch': [cb1, cb2] }

export const initializeSocket = (id) => {
	// If ID is provided, update our stored ID. If not, use the stored one (for reconnects)
	if (id) userId = id;

	// Prevent duplicate connections if already open/connecting
	if (
		socket &&
		(socket.readyState === WebSocket.OPEN ||
			socket.readyState === WebSocket.CONNECTING)
	) {
		return;
	}

	if (reconnectTimer) {
		clearTimeout(reconnectTimer);
		reconnectTimer = null;
	}

	// 1. Connect via Standard WebSocket with userId query param
	console.log("Connecting to WebSocket...");
	socket = new WebSocket(`${SOCKET_URL}?userId=${userId}`);

	socket.onopen = () => {
		console.log("WebSocket Connected");
	};

	socket.onclose = () => {
		console.log("WebSocket Disconnected");
		// If userId is still set (meaning user didn't logout), try to reconnect
		if (userId) {
			console.log("Attempting reconnect in 3s...");
			reconnectTimer = setTimeout(() => {
				initializeSocket(); // Call without ID to use stored userId
			}, 3000);
		}
	};

	socket.onerror = (error) => {
		console.error("WebSocket error:", error);
	};

	// 2. Handle Incoming Messages (Parse JSON -> Trigger Listeners)
	socket.onmessage = (event) => {
		try {
			const data = JSON.parse(event.data); // Expected: { type: "eventName", payload: ... }
			const eventName = data.type;
			const payload = data.payload;

			// If we have mapped the ID in axios, we should probably do it here too for consistency
			if (payload && payload.id && !payload._id) {
				payload._id = payload.id;
			}

			if (listeners[eventName]) {
				listeners[eventName].forEach((callback) => callback(payload));
			}
		} catch (error) {
			console.error("Socket message parse error", error);
		}
	};
};

// 3. Create a Facade that mimics Socket.IO's API (getSocket().on...)
export const getSocket = () => {
	if (!socket) {
		// Return a safe dummy if socket is completely missing to prevent immediate crashes
		return {
			on: () => {},
			off: () => {},
			disconnect: () => {},
			raw: { readyState: 3, send: () => {} }, // 3 = CLOSED
		};
	}

	return {
		// Mimic socket.on
		on: (eventName, callback) => {
			if (!listeners[eventName]) {
				listeners[eventName] = [];
			}
			listeners[eventName].push(callback);
		},
		// Mimic socket.off
		off: (eventName) => {
			delete listeners[eventName];
		},
		// Mimic socket.disconnect
		disconnect: () => {
			if (socket) socket.close();
			socket = null;
		},
		// Expose raw socket if needed
		raw: socket,
	};
};

export const disconnectSocket = () => {
	// Clear userId to prevent reconnection loop
	userId = null;
	if (reconnectTimer) clearTimeout(reconnectTimer);

	if (socket) {
		socket.close();
		socket = null;
	}
};
