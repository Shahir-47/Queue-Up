import { useEffect } from "react";

// App components
import Sidebar from "../components/Sidebar";
import { Header } from "../components/Header";
import SwipeArea from "../components/SwipeArea";
import SwipeFeedback from "../components/SwipeFeedback";

// Global state stores
import { useMatchStore } from "../store/useMatchStore";
import { useAuthStore } from "../store/useAuthStore";

// Icon
import { Frown } from "lucide-react";

const HomePage = () => {
	const {
		isLoadingUserProfiles,
		getUserProfiles,
		userProfiles,
		subscribeToNewMatches,
		unsubscribeFromNewMatches,
		subscribeToNewUserProfiles,
		unsubscribeFromNewUserProfiles,
	} = useMatchStore();

	const { authUser } = useAuthStore();

	/**
	 * Fetch user profiles (potential matches) when the component first mounts.
	 * This ensures the swipe deck is filled as soon as the page loads.
	 */
	useEffect(() => {
		getUserProfiles();
	}, [getUserProfiles]);

	/**
	 * Subscribe to real-time "newMatch" events via WebSocket
	 * Only sets up subscription if the user is authenticated.
	 * Automatically unsubscribes when component unmount or when the user logs out.
	 */
	useEffect(() => {
		authUser && subscribeToNewMatches();
		return () => {
			unsubscribeFromNewMatches();
		};
	}, [subscribeToNewMatches, unsubscribeFromNewMatches, authUser]);

	/**
	 * Subscribe to real-time "newUserProfile" events via WebSocket
	 * Only sets up subscription if the user is authenticated.
	 * Automatically unsubscribes when component unmount or when the user logs out.
	 */
	useEffect(() => {
		authUser && subscribeToNewUserProfiles();
		return () => {
			unsubscribeFromNewUserProfiles();
		};
	}, [subscribeToNewUserProfiles, unsubscribeFromNewUserProfiles, authUser]);

	const hasProfiles = userProfiles.length > 0;

	return (
		<div className="flex flex-col lg:flex-row min-h-screen bg-gradient-to-br from-[#0b0b0b] via-[#121212] to-[#1a1a1a] overflow-hidden">
			<Sidebar />
			<div className="flex-grow flex flex-col overflow-hidden">
				<Header />
				<main className="flex-grow flex flex-col gap-10 justify-center items-center p-4 relative overflow-hidden">
					{/* Display all users if available */}
					{hasProfiles && (
						<>
							<SwipeArea />

							{/* This component tells if you liked or disliked the user shown */}
							<SwipeFeedback />
						</>
					)}

					{/* Display no users UI if no users available and not in a loading state */}
					{!hasProfiles && !isLoadingUserProfiles && (
						<NoMoreProfiles />
					)}

					{/* Display loading animation if in a loading state */}
					{!hasProfiles && isLoadingUserProfiles && <LoadingUI />}
				</main>
			</div>
		</div>
	);
};

// no users left to show UI
const NoMoreProfiles = () => (
	<div className="flex flex-col items-center justify-center h-full text-center p-8">
		<Frown className="text-[#1DB954] mb-6" size={80} />
		<h2 className="text-3xl font-bold text-gray-100 mb-4">
			Woah there, speedy fingers!
		</h2>
		<p className="text-xl text-gray-400 mb-6">
			Looks like you've swiped through all the profiles. Don't worry, more are
			coming soon!
		</p>
	</div>
);

// loading UI
const LoadingUI = () => {
	return (
		<div className="relative w-full max-w-sm h-[28rem] animate-pulse">
			<div className="card bg-[#181818] w-96 h-[28rem] rounded-lg overflow-hidden border border-[#2a2a2a] shadow-sm">
				<div className="px-4 pt-4 h-3/4">
					{/* big image placeholder */}
					<div className="w-full h-full bg-[#2a2a2a] rounded-lg" />
				</div>
				<div className="card-body bg-gradient-to-b from-[#181818] to-[#121212] p-4">
					<div className="space-y-2">
						{/* text line placeholders */}
						<div className="h-6 bg-[#2a2a2a] rounded w-3/4" />
						<div className="h-4 bg-[#2a2a2a] rounded w-1/2" />
					</div>
				</div>
			</div>
		</div>
	);
};

export default HomePage;
