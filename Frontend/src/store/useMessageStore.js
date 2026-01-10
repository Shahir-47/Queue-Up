import { create } from "zustand";
import { axiosInstance } from "../lib/axios";
import toast from "react-hot-toast";
import { getSocket } from "../socket/socket.client";
import { useAuthStore } from "./useAuthStore";

export const useMessageStore = create((set, get) => ({
	messages: [], // List of all messages in the current conversation
	loading: true, // Tracks loading state while fetching messages
	isTyping: false,
	typingTimeout: null,

	sendMessage: async (receiverId, content, attachments, previewUrls = []) => {
		try {
			// show message in chat
			set((state) => ({
				messages: [
					...state.messages,
					{
						_id: Date.now(),
						sender: useAuthStore.getState().authUser._id,
						receiver: receiverId,
						content,
						attachments,
						linkPreviews: previewUrls,
						createdAt: new Date().toISOString(),
					},
				],
			}));

			// send message to backend
			await axiosInstance.post("/messages/send", {
				receiverId,
				content,
				attachments,
				previewUrls,
			});
		} catch (error) {
			toast.error(error.response.data.message || "Something went wrong");
		}
	},
	getMessages: async (userId) => {
		try {
			set({ loading: true, isTyping: false });
			const res = await axiosInstance.get(`/messages/conversation/${userId}`);
			set({ messages: res.data.messages });
		} catch (error) {
			console.log(error);
			set({ messages: [] });
		} finally {
			set({ loading: false });
		}
	},

	//these two make it real time
	subscribeToMessages: () => {
		try {
			const socket = getSocket();
			socket.on("newMessage", (message) => {
				set((state) => ({
					messages: [...state.messages, message],
					isTyping: false,
				}));
			});

			socket.on("typing", () => {
				set({ isTyping: true });

				// Clear existing timeout if any (so we don't flicker)
				if (get().typingTimeout) clearTimeout(get().typingTimeout);

				// Set new timeout to hide indicator after 3 seconds
				const timeout = setTimeout(() => {
					set({ isTyping: false });
				}, 3000);

				set({ typingTimeout: timeout });
			});
		} catch (error) {
			console.log("Error subscribing to messages: ", error);
		}
	},

	unsubscribeFromMessages: () => {
		try {
			const socket = getSocket();
			socket.off("newMessage");
			socket.off("typing");
		} catch (error) {
			console.log("Error unsubscribing from messages: ", error);
		}
	},
}));
