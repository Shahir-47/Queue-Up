import { useAuthStore } from "../store/useAuthStore";
import { useState, useRef, useEffect } from "react";
import { Link } from "react-router-dom";
import { AudioLines, User, LogOut, Menu } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";

export const Header = () => {
	const { authUser, logout } = useAuthStore(); // Get the authUser and logout function from the store

	const [dropdownOpen, setDropdownOpen] = useState(false); // state to toggle dropdown menu
	const [mobileMenuOpen, setMobileMenuOpen] = useState(false); // state to toggle mobile menu
	const dropdownRef = useRef(null); // ref to manage dropdown menu

	// Detects clicks outside the dropdown and closes it
	useEffect(() => {
		const handleClickOutside = (event) => {
			if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
				setDropdownOpen(false);
			}
		};

		document.addEventListener("mousedown", handleClickOutside);

		return () => document.removeEventListener("mousedown", handleClickOutside);
	}, []);

	return (
		<header className="bg-gradient-to-r from-[#0b0b0b] via-[#121212] to-[#1a1a1a] shadow-lg border-b border-[#1DB954]/40">
			{/* Logo and title */}
			<div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
				<div className="flex justify-between items-center py-3">
					<div className="flex items-center flex-1 justify-center md:justify-start">
						{/* Logo */}
						<Link
							to="/"
							className="group flex items-center space-x-2 rounded-md transition"
						>
							<AudioLines className="w-10 h-10 text-[#1DB954] group-hover:scale-105 transition-transform" />
							<div className="ml-3 lg:ml-0 flex flex-col leading-tight">
								<span className="text-2xl font-bold text-white sm:inline group-hover:scale-101 transition-transform">
									Queue Up
								</span>
								<span className="hidden sm:block text-[0.65rem] uppercase tracking-[0.25em] text-gray-400">
									Meet through music
								</span>
							</div>
						</Link>
					</div>

					{/* Show user image and name on larger screens */}
					<div className="hidden md:flex items-center space-x-4">
						{/* show this if the user is logged in */}
						{authUser ? (
							<div className="relative" ref={dropdownRef}>
								<button
									onClick={() => setDropdownOpen(!dropdownOpen)}
									className="flex items-center space-x-2 focus:outline-none cursor-pointer hover:bg-[#1DB954]/10 rounded-md px-3 py-2 transition"
								>
									<img
										src={authUser.image || "/avatar.png"} // Default image if none provided
										className="h-10 w-10 object-cover rounded-full border-2 border-[#1DB954]"
										alt="User image"
									/>
									<span className="text-white font-medium">
										{authUser.name}
									</span>
								</button>

								{/* Dropdown menu */}
								<AnimatePresence>
									{dropdownOpen && (
										<motion.div
											initial={{ opacity: 0, y: -20 }}
											animate={{
												opacity: 1,
												y: 0,
												transition: { type: "spring", stiffness: 125 },
											}}
											exit={{
												opacity: 0,
												y: -10,
												transition: { duration: 0.2 },
											}}
											className="absolute right-0 mt-2 w-48 bg-[#181818] rounded-md shadow-lg py-1 z-10 border border-[#2a2a2a]"
										>
											{/* Link to profile page */}
											<Link
												to="/profile"
												className="px-4 py-2 text-sm text-gray-200 hover:bg-[#1f1f1f] flex items-center"
												onClick={() => setDropdownOpen(false)}
											>
												<User className="mr-2" size={16} />
												Profile
											</Link>

											{/* Logout button */}
											<button
												onClick={logout}
												className="w-full text-left px-4 py-2 text-sm text-gray-200 hover:bg-[#1f1f1f] cursor-pointer flex items-center"
											>
												<LogOut className="mr-2" size={16} />
												Logout
											</button>
										</motion.div>
									)}
								</AnimatePresence>
							</div>
						) : (
							// Show login and signup links if user is not logged in
							<>
								<Link
									to="/auth"
									className="text-white hover:text-[#1DB954] transition duration-150 ease-in-out"
								>
									Login
								</Link>
								<Link
									to="/auth"
									className="bg-[#1DB954] text-black px-4 py-2 rounded-full font-medium hover:bg-[#1ed760] transition duration-150 ease-in-out"
								>
									Sign Up
								</Link>
							</>
						)}
					</div>

					{/* Mobile menu button */}
					<div className="md:hidden">
						<button
							onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
							className="text-white focus:otline-none"
						>
							<Menu className="size-6" />
						</button>
					</div>
				</div>
			</div>

			{/* MOBILE MENU */}

			<AnimatePresence>
				{mobileMenuOpen && (
					<motion.div
						initial={{ opacity: 0, y: -50 }}
						animate={{
							opacity: 1,
							y: 0,
							transition: { type: "spring", stiffness: 150 },
						}}
						exit={{ opacity: 0, y: -20, transition: { duration: 0.2 } }}
						className="md:hidden bg-[#121212] border-t border-[#1DB954]/40"
					>
						<div className="px-2 pt-2 pb-3 space-y-1 sm:px-3">
							{/* show this if the user is logged in */}
							{authUser ? (
								<>
									<Link
										to="/profile"
										className="block px-3 py-2 rounded-md text-base font-medium text-white hover:bg-[#1f1f1f]"
										onClick={() => setMobileMenuOpen(false)}
									>
										Profile
									</Link>
									<button
										onClick={() => {
											logout();
											setMobileMenuOpen(false);
										}}
										className="block w-full text-left px-3 py-2 rounded-md text-base font-medium text-white hover:bg-[#1f1f1f]"
									>
										Logout
									</button>
								</>
							) : (
								// Show login and signup links if user is not logged in
								<>
									<Link
										to="/auth"
										className="block px-3 py-2 rounded-md text-base font-medium text-white hover:bg-[#1f1f1f]"
										onClick={() => setMobileMenuOpen(false)}
									>
										Login
									</Link>
									<Link
										to="/auth"
										className="block px-3 py-2 rounded-md text-base font-medium text-white hover:bg-[#1f1f1f]"
										onClick={() => setMobileMenuOpen(false)}
									>
										Sign Up
									</Link>
								</>
							)}
						</div>
					</motion.div>
				)}
			</AnimatePresence>
		</header>
	);
};
