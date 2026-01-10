import { create } from "zustand";
import { axiosInstance } from "../lib/axios";
import toast from "react-hot-toast";
import {
	disconnectSocket,
	initializeSocket,
	getSocket,
} from "../socket/socket.client";

export const useAuthStore = create((set) => ({
	authUser: null,
	checkingAuth: true,
	loading: false,
	onlineUsers: [], // Store online user IDs here

	signup: async (signupData) => {
		try {
			set({ loading: true });
			const res = await axiosInstance.post("/auth/signup", signupData);
			set({ authUser: res.data.user });
			initializeSocket(res.data.user._id);
			toast.success("Account created successfully!");
		} catch (error) {
			toast.error(error.response.data.message || "Something went wrong!");
		} finally {
			set({ loading: false });
		}
	},

	login: async (loginData) => {
		try {
			set({ loading: true });
			const res = await axiosInstance.post("/auth/login", loginData);
			set({ authUser: res.data.user });
			initializeSocket(res.data.user._id);
			toast.success("Logged in successfully!");
		} catch (error) {
			toast.error(error.response.data.message || "Something went wrong!");
		} finally {
			set({ loading: false });
		}
	},

	logout: async () => {
		try {
			const res = await axiosInstance.post("/auth/logout");
			disconnectSocket();
			if (res.status === 200) set({ authUser: null, onlineUsers: [] });
		} catch (error) {
			toast.error(error.response.data.message || "Something went wrong!");
		}
	},

	checkAuth: async () => {
		try {
			const res = await axiosInstance.get("/auth/me");
			initializeSocket(res.data.user._id);
			set({ authUser: res.data.user });
		} catch {
			set({ authUser: null });
		} finally {
			set({ checkingAuth: false });
		}
	},

	subscribeToOnlineUsers: () => {
		try {
			const socket = getSocket();

			// 1. Get initial list of online users
			socket.on("getOnlineUsers", (userIds) => {
				set({ onlineUsers: userIds });
			});

			// 2. Listen for new connections
			socket.on("userOnline", (userId) => {
				set((state) => ({
					onlineUsers: [...state.onlineUsers, userId],
				}));
			});

			// 3. Listen for disconnections
			socket.on("userOffline", (userId) => {
				set((state) => ({
					onlineUsers: state.onlineUsers.filter((id) => id !== userId),
				}));
			});
		} catch (error) {
			console.log("Error subscribing to online users:", error);
		}
	},

	unsubscribeFromOnlineUsers: () => {
		try {
			const socket = getSocket();
			socket.off("getOnlineUsers");
			socket.off("userOnline");
			socket.off("userOffline");
		} catch (error) {
			console.log(error);
		}
	},

	setAuthUser: (user) => set({ authUser: user }),
}));
