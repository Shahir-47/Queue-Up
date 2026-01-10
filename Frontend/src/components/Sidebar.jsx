import { useState, useEffect } from "react";
import { X, Loader, Disc3, MessageCircle } from "lucide-react";
import { Link } from "react-router-dom";
import { useMatchStore } from "../store/useMatchStore";
import { useAuthStore } from "../store/useAuthStore";

const Sidebar = () => {
	const [isOpen, setIsOpen] = useState(false);
	const toggleSidebar = () => setIsOpen(!isOpen);

	const { getMyMatches, matches, isLoadingMyMatches } = useMatchStore();
	const { onlineUsers } = useAuthStore();

	useEffect(() => {
		getMyMatches();
	}, [getMyMatches]);

	return (
		<>
			<div
				className={`fixed inset-y-0 left-0 z-10 w-64 bg-[#121212] border-r border-[#1DB954]/20 shadow-md overflow-hidden transition-transform duration-300 ease-in-out ${
					isOpen ? "translate-x-0" : "-translate-x-full"
				} lg:translate-x-0 lg:static lg:w-1/4`}
			>
				<div className="flex flex-col h-full">
					{/* Header */}
					<div className="p-4 pb-[20px] border-b border-[#1DB954]/30 flex justify-between items-center">
						<h2 className="text-xl font-bold text-[#1DB954]">Music Matches</h2>
						<button
							className="lg:hidden p-1 text-gray-400 hover:text-gray-200 focus:outline-none"
							onClick={toggleSidebar}
						>
							<X size={24} />
						</button>
					</div>

					<div className="flex-grow overflow-y-auto p-4 z-10 relative">
						{isLoadingMyMatches ? (
							<LoadingState />
						) : matches.length === 0 ? (
							<NoMatchesFound />
						) : (
							matches.map((match) => {
								const isOnline = onlineUsers.includes(match._id) || match.isBot;

								return (
									<Link key={match._id} to={`/chat/${match._id}`}>
										<div className="flex items-center mb-4 cursor-pointer hover:bg-[#1f1f1f] p-2 rounded-lg transition-colors duration-300">
											{/* Avatar Container */}
											<div className="relative mr-3">
												<img
													src={match.image || "/avatar.png"}
													alt="User avatar"
													className="size-12 object-cover rounded-full border-2 border-[#1DB954]/60"
												/>
												{/* Green Dot Indicator */}
												{isOnline && (
													<span className="absolute bottom-0 right-0 size-3 bg-green-500 border-2 border-[#121212] rounded-full"></span>
												)}
											</div>

											<h3 className="font-semibold text-gray-100">
												{match.name}
											</h3>
										</div>
									</Link>
								);
							})
						)}
					</div>
				</div>
			</div>

			<button
				className="lg:hidden fixed top-3 md:top-5 left-4 p-1 pl-1.5 bg-[#1DB954] text-black rounded-md z-0"
				onClick={toggleSidebar}
			>
				<MessageCircle size={30} />
			</button>
		</>
	);
};

// UI if user has no matches
const NoMatchesFound = () => (
	<div className="flex flex-col items-center justify-center h-full text-center">
		<Disc3 className="text-[#1DB954] mb-4" size={78} />
		<h3 className="text-xl font-semibold text-gray-200 mb-2">
			No music matches yet
		</h3>
		<p className="text-gray-400 max-w-xs">Keep swiping</p>
	</div>
);

// Custom component for displaying loading animation
const LoadingState = () => (
	<div className="flex flex-col items-center justify-center h-full text-center">
		<Loader className="text-[#1DB954] mb-4 animate-spin" size={48} />
		<h3 className="text-xl font-semibold text-gray-200 mb-2">
			Finding matches
		</h3>
		<p className="text-gray-400 max-w-xs">Matching your taste</p>
	</div>
);

export default Sidebar;
