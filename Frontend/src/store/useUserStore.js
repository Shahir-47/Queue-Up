import { create } from "zustand";
import { axiosInstance } from "../lib/axios";
import toast from "react-hot-toast";
import { useAuthStore } from "./useAuthStore";

export const useUserStore = create((set) => ({
	loading: false,

	updateProfile: async (data) => {
		try {
			set({ loading: true }); // Set loading to true when starting the update process
			const res = await axiosInstance.put("/users/update", data); // Sends a PUT request to the backend to update the user data
			useAuthStore.getState().setAuthUser(res.data.user); // updates user info immediately when profile edited
			toast.success("Profile updated successfully!"); // Show success message
		} catch (error) {
			toast.error(error.response.data.message || "Something went wrong!"); // Show error message
		} finally {
			set({ loading: false }); // Set loading to false when the update process is completed
		}
	},
}));
