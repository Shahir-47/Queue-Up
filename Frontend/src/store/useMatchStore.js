import { create } from "zustand";
import { axiosInstance } from "../lib/axios";
import toast from "react-hot-toast";
import { getSocket } from "../socket/socket.client";

export const useMatchStore = create((set, get) => ({
	matches: [], // list of matches
	isLoadingUserProfiles: false,
	userProfiles: [],
	swipeFeedback: null,

	getMyMatches: async () => {
		try {
			set({ isLoadingMyMatches: true }); // Set isLoadingMyMatches to true when fetching user's matches
			const res = await axiosInstance.get("/matches"); //  Sends a GET request to the backend to fetch all matches
			set({ matches: res.data.matches }); // successful, update matches array with the user's matches fetched from the api
		} catch (error) {
			set({ matches: [] }); // Reset matches when an error occurs
			toast.error(error.response.data.message || "Something went wrong!"); // Show error message
		} finally {
			set({ isLoadingMyMatches: false }); // Set isLoadingMyMatches to false when the fetching process is complete
		}
	},

	getUserProfiles: async (options = {}) => {
		const { silent = false, merge = false } = options;
		const hasProfiles = get().userProfiles.length > 0;
		const showLoading = !silent && !hasProfiles;

		try {
			if (showLoading) {
				set({ isLoadingUserProfiles: true });
			}
			const res = await axiosInstance.get("/matches/user-profiles"); //  Sends a GET request to the backend to fetch all other users
			const nextProfiles = res.data?.users ?? [];

			const latestProfiles = get().userProfiles;
			if (merge && latestProfiles.length > 0) {
				const existingIds = new Set(latestProfiles.map((profile) => profile._id));
				const appendedProfiles = nextProfiles.filter(
					(profile) => !existingIds.has(profile._id)
				);

				if (appendedProfiles.length > 0) {
					set({ userProfiles: [...latestProfiles, ...appendedProfiles] });
				}
			} else {
				set({ userProfiles: nextProfiles }); // successful, update userProfiles array with the users fetched from the api
			}
		} catch (error) {
			if (!hasProfiles) {
				set({ userProfiles: [] }); // Reset userProfiles when an error occurs
			}
			toast.error(error.response.data.message || "Something went wrong!"); // Show error message
		} finally {
			if (showLoading) {
				set({ isLoadingUserProfiles: false });
			}
		}
	},

	swipeLeft: async (user) => {
		try {
			set({ swipeFeedback: "passed" });

			set((state) => ({
				userProfiles: state.userProfiles.filter((p) => p._id !== user._id),
			}));

			await axiosInstance.post("/matches/swipe-left/" + user._id); // Sends a POST request to the backend to put the other user in the disliked array of the current user
		} catch (error) {
			console.log(error);
			toast.error("Failed to swipe left!"); // Show error message
		} finally {
			setTimeout(() => set({ swipeFeedback: null }), 1500); // show 'disliked' message for 1.5 sec
		}
	},

	swipeRight: async (user) => {
		try {
			set({ swipeFeedback: "liked" });

			set((state) => ({
				userProfiles: state.userProfiles.filter((p) => p._id !== user._id),
			}));

			await axiosInstance.post("/matches/swipe-right/" + user._id); // Sends a POST request to the backend to put the other user in the liked array of the current user
		} catch (error) {
			console.log(error);
			toast.error("Failed to swipe right!"); // Show error message
		} finally {
			setTimeout(() => set({ swipeFeedback: null }), 1500); // show 'liked' message for 1.5 sec
		}
	},

	//listen for new matches
	subscribeToNewMatches: () => {
		try {
			const socket = getSocket();

			//listening for events from backend called newMatch
			socket.on("newMatch", (newMatch) => {
				const currentMatches = get().matches;
				const isDuplicate = currentMatches.some((m) => m._id === newMatch._id);

				if (!isDuplicate) {
					set((state) => ({
						matches: [...state.matches, newMatch],
					}));
					toast.success("You got a new match!");
				}
			});
		} catch (error) {
			console.log(error);
		}
	},

	//when we log out, we no longer listen for new matches
	unsubscribeFromNewMatches: () => {
		try {
			const socket = getSocket();
			socket.off("newMatch");
		} catch (error) {
			console.log(error);
		}
	},

	subscribeToNewUserProfiles: () => {
		try {
			const socket = getSocket();

			//listening for events from backend called newMatch
			socket.on("newUserProfile", () => {
				// Fetch new user profiles without interrupting the swipe deck.
				useMatchStore.getState().getUserProfiles({ silent: true });
			});
		} catch (error) {
			console.log(error);
		}
	},

	//when we log out, we no longer listen for new matches
	unsubscribeFromNewUserProfiles: () => {
		try {
			const socket = getSocket();
			socket.off("newUserProfile");
		} catch (error) {
			console.log(error);
		}
	},
}));
