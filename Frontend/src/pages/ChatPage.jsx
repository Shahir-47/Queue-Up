import { useEffect, useState, useRef, useLayoutEffect } from "react";
import { ArrowRight } from "lucide-react";
import { AnimatePresence, motion } from "framer-motion";
import { Header } from "../components/Header";
import { useAuthStore } from "../store/useAuthStore";
import { useMatchStore } from "../store/useMatchStore";
import { useMessageStore } from "../store/useMessageStore";
import { Link, useParams } from "react-router-dom";
import { Loader, UserX } from "lucide-react";
import MessageInput from "../components/MessageInput";
import PreviewAttachment from "../components/PreviewAttachment";
import ViewAttachmentModal from "../components/ViewAttachmentModal";
import LinkPreviewCard from "../components/LinkPreviewCard";
import Masonry from "react-masonry-css";
import { axiosInstance } from "../lib/axios";

const masonryBreakpoints = {
	default: 2, // two columns normally
	768: 2, // ≥768px still 2 cols
	480: 1, // <480px → 1 col
};

const playerVariants = {
	enter: (direction) => ({
		x: direction > 0 ? 48 : -48,
		opacity: 0,
	}),
	center: { x: 0, opacity: 1 },
	exit: (direction) => ({
		x: direction > 0 ? -48 : 48,
		opacity: 0,
	}),
};

// filter out Kanye / Dexter from any list
const filterBad = (arr = []) =>
	arr.filter((it) => {
		const n = it.name.toLowerCase();
		return n;
	});

const ChatPage = () => {
	const { getMyMatches, matches, isLoadingMyMatches } = useMatchStore();

	const {
		messages,
		sendMessage,
		getMessages,
		subscribeToMessages,
		unsubscribeFromMessages,
		isTyping,
	} = useMessageStore();
	const { authUser, onlineUsers } = useAuthStore();
	const [trackIndex, setTrackIndex] = useState(0);
	const [trackDirection, setTrackDirection] = useState(1);
	const [loadedTrackId, setLoadedTrackId] = useState(null);
	const [viewAttachment, setViewAttachment] = useState(null);
	const [linkPreviewMap, setLinkPreviewMap] = useState({}); // message._id -> [{ url, preview }]

	const messagesEndRef = useRef(null); // dummy div to scroll to the bottom of the chat
	const initialScrollRef = useRef(true);
	const playerRevealTimeoutRef = useRef(null);
	const currentTrackIdRef = useRef(null);
	const playerIframeRef = useRef(null);
	const playerResizeObserverRef = useRef(null);
	const iframeLoadedRef = useRef(false);

	// Get the match ID from the URL parameters
	const { id } = useParams();

	// Find the matched user from the matches array
	// This is used to display the match's name and image in the chat header
	const match = matches.find((m) => m?._id?.toString() === id);

	// Determine if the matched user is online
	const isOnline = match
		? onlineUsers.includes(match._id) || match.isBot
		: false;

	// Handle opening the attachment modal
	const handleViewAttachmentClick = (attachment) => {
		setViewAttachment(attachment);
	};

	// Handle closing the attachment modal
	const handleCloseModal = () => {
		setViewAttachment(null);
	};

	// Fetch matches and messages when the component mounts
	useEffect(() => {
		if (authUser && id) {
			getMyMatches(); // Fetch matches to populate the matches array above
			getMessages(id); // Fetch messages for the selected match
			subscribeToMessages(); // Subscribe to real-time messages
		}

		return () => {
			unsubscribeFromMessages(); // Unsubscribe from real-time messages when the component unmounts
		};
	}, [
		getMyMatches,
		authUser,
		getMessages,
		subscribeToMessages,
		unsubscribeFromMessages,
		id,
	]);

	useEffect(() => {
		initialScrollRef.current = true;
	}, [id]);

	// Scroll to the bottom of the chat on initial load and new messages
	useLayoutEffect(() => {
		const behavior = initialScrollRef.current ? "auto" : "smooth";
		messagesEndRef.current?.scrollIntoView({ behavior, block: "end" });
		if ((messages.length > 0 || isTyping) && initialScrollRef.current) {
			initialScrollRef.current = false;
		}
	}, [messages, isTyping]);

	useEffect(() => {
		return () => {
			if (playerRevealTimeoutRef.current) {
				clearTimeout(playerRevealTimeoutRef.current);
			}
			if (playerResizeObserverRef.current) {
				playerResizeObserverRef.current.disconnect();
				playerResizeObserverRef.current = null;
			}
		};
	}, []);

	useEffect(() => {
		const fetchPreviews = async () => {
			for (const msg of messages) {
				if (!msg.linkPreviews?.length || linkPreviewMap[msg._id]) continue;

				const results = await Promise.all(
					msg.linkPreviews.map((url) =>
						axiosInstance
							.post("/link-preview", { url })
							.then((r) => ({ url, preview: r.data }))
							.catch(() => null)
					)
				);

				setLinkPreviewMap((prev) => ({
					...prev,
					[msg._id]: results.filter(Boolean),
				}));
			}
		};
		fetchPreviews();
	}, [messages]);

	const tracks = filterBad(match?.commonTracks);
	const currentTrack = tracks[trackIndex];
	const currentTrackId = currentTrack?.id;
	currentTrackIdRef.current = currentTrackId;
	const isPlayerLoaded = loadedTrackId === currentTrackId;

	const schedulePlayerReveal = (trackId) => {
		if (!trackId) return;
		if (playerRevealTimeoutRef.current) {
			clearTimeout(playerRevealTimeoutRef.current);
		}
		playerRevealTimeoutRef.current = setTimeout(() => {
			if (iframeLoadedRef.current && trackId === currentTrackIdRef.current) {
				setLoadedTrackId(trackId);
			}
		});
	};

	useEffect(() => {
		setLoadedTrackId(null);
		iframeLoadedRef.current = false;

		if (playerRevealTimeoutRef.current) {
			clearTimeout(playerRevealTimeoutRef.current);
		}

		if (playerResizeObserverRef.current) {
			playerResizeObserverRef.current.disconnect();
			playerResizeObserverRef.current = null;
		}

		const iframeEl = playerIframeRef.current;
		if (!iframeEl || typeof ResizeObserver === "undefined") return;

		playerResizeObserverRef.current = new ResizeObserver(() => {
			if (!iframeLoadedRef.current) return;
			schedulePlayerReveal(currentTrackIdRef.current);
		});

		playerResizeObserverRef.current.observe(iframeEl);

		return () => {
			if (playerResizeObserverRef.current) {
				playerResizeObserverRef.current.disconnect();
				playerResizeObserverRef.current = null;
			}
		};
	}, [currentTrackId]);

	if (isLoadingMyMatches) return <LoadingMessagesUI />;
	if (!match) return <MatchNotFound />;

	const handlePlayerLoad = (trackId) => {
		if (!trackId || trackId !== currentTrackIdRef.current) return;
		iframeLoadedRef.current = true;
		schedulePlayerReveal(trackId);
	};

	const handlePrevTrack = () => {
		setTrackDirection(-1);
		setTrackIndex((i) => (i - 1 + tracks.length) % tracks.length);
	};

	const handleNextTrack = () => {
		setTrackDirection(1);
		setTrackIndex((i) => (i + 1) % tracks.length);
	};

	return (
		//UI stuff
		<div className="flex flex-col h-screen bg-[#0b0b0b]">
			<Header />

			<div className="flex-grow flex flex-col p-4 md:p-6 lg:p-8 overflow-hidden max-w-4xl mx-auto w-full">
				{/* Chat Header */}
				<div className="flex items-center mb-4 bg-[#181818] border border-[#2a2a2a] rounded-lg shadow py-2 px-6">
					<div className="relative">
						<img
							src={match.image || "/avatar.png"}
							className="w-12 h-12 object-cover rounded-full mr-3 border-2 border-[#1DB954]"
						/>
						{/* Green Dot */}
						{isOnline && (
							<span className="absolute bottom-0 right-3 size-3 bg-green-500 border-2 border-[#181818] rounded-full"></span>
						)}
					</div>

					<div className="flex flex-col">
						<h2 className="text-xl font-semibold text-gray-100 leading-tight">
							{match.name}
						</h2>
						<span className="text-xs text-gray-400">
							{isOnline ? "Online" : "Offline"}
						</span>
					</div>

					{tracks.length > 0 && (
						<div className="ml-auto flex items-center space-x-2">
							{/* Previous Button */}
							<button
								onClick={handlePrevTrack}
								className="p-2 bg-[#1f1f1f] hover:bg-[#2a2a2a] rounded-full cursor-pointer text-[#1DB954]"
								title="Previous shared track"
							>
								<ArrowRight size={20} className="rotate-180" />
							</button>

							{/* Mini Spotify Embed */}
							<AnimatePresence
								mode="wait"
								initial={false}
								custom={trackDirection}
							>
								<motion.div
									key={currentTrackId}
									className="relative rounded-lg overflow-hidden bg-[#121212]"
									custom={trackDirection}
									variants={playerVariants}
									initial="enter"
									animate="center"
									exit="exit"
									transition={{ type: "spring", stiffness: 260, damping: 26 }}
								>
									<iframe
										ref={playerIframeRef}
										src={`https://open.spotify.com/embed/track/${currentTrackId}?utm_source=queue-up&theme=0`}
										height="80"
										frameBorder="0"
										allow="encrypted-media"
										title={currentTrack.name}
										onLoad={() => handlePlayerLoad(currentTrackId)}
										className={`block bg-[#121212] -m-[2px] transition-opacity duration-200 ${
											isPlayerLoaded ? "opacity-100" : "opacity-0"
										}`}
									/>
									<div
										aria-hidden="true"
										className={`absolute inset-0 z-10 bg-[#121212] transition-opacity duration-200 ${
											isPlayerLoaded
												? "opacity-0 pointer-events-none"
												: "opacity-100"
										}`}
									/>
								</motion.div>
							</AnimatePresence>

							{/* Next Button */}
							<button
								onClick={handleNextTrack}
								className="p-2 bg-[#1f1f1f] hover:bg-[#2a2a2a] rounded-full cursor-pointer text-[#1DB954]"
								title="Next shared track"
							>
								<ArrowRight size={20} />
							</button>
						</div>
					)}
				</div>

				<div className="flex-grow overflow-y-auto mb-4 bg-[#121212] border border-[#2a2a2a] rounded-lg shadow p-4">
					{/* No messages yet */}
					{messages.length === 0 ? (
						<div className="text-center text-gray-400 p-4">
							<p className="text-lg mb-8">
								Shared taste{" "}
								<span className="font-semibold">
									{filterBad(match.commonArtists)
										.map((a) => a.name)
										.join(", ") || "music"}
								</span>{" "}
								{tracks.length > 0 ? "Share and play" : "Share and say hey"}
							</p>

							<div className="inline-grid grid-cols-1 gap-2 sm:grid-cols-2">
								{/* 1) Ask about a shared artist */}
								{filterBad(match.commonArtists)
									.slice(0, 2)
									.map((a, i) => (
										<button
											key={`artist-${i}`}
											onClick={() =>
												sendMessage(
													match._id,
													`We both love ${a.name}. Swap top tracks?`,
													[],
													[]
												)
											}
											className="px-4 py-2 bg-[#1f1f1f] text-gray-200 border border-[#2a2a2a] hover:bg-[#2a2a2a] rounded-full text-sm transition"
										>
											Top tracks: {a.name}
										</button>
									))}

								{/* 2) Ask about a shared track */}
								{filterBad(match.commonTracks)
									.slice(0, 2)
									.map((t, i) => (
										<button
											key={`track-${i}`}
											onClick={() =>
												sendMessage(
													match._id,
													`"${t.name}" is on your list too. Play it first?`,
													[],
													[]
												)
											}
											className="px-4 py-2 bg-[#1f1f1f] text-gray-200 border border-[#2a2a2a] hover:bg-[#2a2a2a] rounded-full text-sm transition"
										>
											Play track: "{t.name}"
										</button>
									))}

								{/* 3) Ask about a saved track */}
								{filterBad(match.commonSaved)
									.slice(0, 2)
									.map((s, i) => (
										<button
											key={`saved-${i}`}
											onClick={() =>
												sendMessage(
													match._id,
													`We both saved "${s.name}". What's on repeat for you?`,
													[],
													[]
												)
											}
											className="px-4 py-2 bg-[#1f1f1f] text-gray-200 border border-[#2a2a2a] hover:bg-[#2a2a2a] rounded-full text-sm transition"
										>
											On repeat: "{s.name}"
										</button>
									))}

								{/* 4) Ask about a followed artist */}
								{filterBad(match.commonFollowed)
									.slice(0, 2)
									.map((f, i) => (
										<button
											key={`followed-${i}`}
											onClick={() =>
												sendMessage(
													match._id,
													`You follow ${f.name}? Favorite album?`,
													[],
													[]
												)
											}
											className="px-4 py-2 bg-[#1f1f1f] text-gray-200 border border-[#2a2a2a] hover:bg-[#2a2a2a] rounded-full text-sm transition"
										>
											Favorite: {f.name}
										</button>
									))}
							</div>
						</div>
					) : (
						<>
							{
								// Map through messages and display them
								messages.map((msg) => {
									const isMe =
										(msg.senderId || msg.sender?._id || msg.sender) ==
										authUser._id;

									return (
										<div
											key={msg._id || msg.id}
											className={`mb-3 ${isMe ? "text-right" : "text-left"}`}
										>
											<span
												// Gives the sent messages a different color from the received ones
												className={`inline-block p-3 rounded-lg max-w-xs lg:max-w-md ${
													isMe
														? "bg-[#1DB954] text-black"
														: "bg-[#1f1f1f] text-gray-100"
												}`}
											>
												{/* If there is an attached file, render the clickable FileAttachment */}
												{/* Attachment Rendering Logic */}
												{msg.attachments?.length > 0 &&
													(() => {
														// 1. NORMALIZE CATEGORIES (Fixes "IMAGE" vs "image" mismatch)
														const normalizedAttachments = msg.attachments.map(
															(att) => ({
																...att,
																// Safely convert backend ENUM (UPPERCASE) to frontend expectation (lowercase)
																category:
																	att.category?.toLowerCase() || "other",
															})
														);

														// 2. Filter using the normalized categories
														const audioItems = normalizedAttachments.filter(
															(a) => a.category === "audio"
														);
														const otherItems = normalizedAttachments.filter(
															(a) => a.category !== "audio"
														);

														return (
															<div>
																{/* Audio Players (Full Width) */}
																{audioItems.map((att, i) => (
																	<div
																		key={`audio-${i}`}
																		className="mb-4 w-full bg-[#0f0f0f] border border-[#2a2a2a] p-1 rounded-md"
																	>
																		<PreviewAttachment attachment={att} />
																	</div>
																))}

																{/* Images/Videos/Files (Masonry) */}
																<Masonry
																	breakpointCols={masonryBreakpoints}
																	className="flex -ml-4"
																	columnClassName="pl-4"
																>
																	{otherItems.map((att, i) => {
																		// Check logic uses lowercase now, so it matches!
																		const sizeClasses = [
																			"image",
																			"video",
																		].includes(att.category)
																			? "w-50 flex items-end justify-center" // Large Preview
																			: "h-12 w-min flex items-center space-x-2"; // Small File Pill

																		return (
																			<div
																				key={`other-${i}`}
																				className={`mb-4 bg-[#0f0f0f] border border-[#2a2a2a] p-1 rounded-md ${sizeClasses}`}
																			>
																				<PreviewAttachment
																					attachment={att}
																					onClick={() =>
																						handleViewAttachmentClick(att)
																					}
																				/>
																			</div>
																		);
																	})}
																</Masonry>
															</div>
														);
													})()}

												{linkPreviewMap[msg._id]?.length > 0 && (
													<div className="mt-2 space-y-2">
														{linkPreviewMap[msg._id].map(
															({ url, preview }, idx) => (
																<LinkPreviewCard
																	key={url + idx}
																	preview={preview}
																/>
															)
														)}
													</div>
												)}

												{msg.content && (
													<div
														className="break-words"
														dangerouslySetInnerHTML={{
															__html: msg.content
																// URLs
																.replace(
																	/(https?:\/\/[^\s]+)/g,
																	(url) =>
																		`<a href="${url}" target="_blank" rel="noopener noreferrer" class="underline text-[#1DB954]">${url}</a>`
																)
																// Emails
																.replace(
																	/\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b/g,
																	(email) =>
																		`<a href="mailto:${email}" class="underline text-[#1DB954]">${email}</a>`
																)
																// Location phrases like "Location: XYZ"
																.replace(
																	/\bLocation:\s*(.+)/gi,
																	(_, location) =>
																		`Location: <a href="https://www.google.com/maps/search/${encodeURIComponent(
																			location
																		)}" target="_blank" class="underline text-[#1DB954]">${location}</a>`
																)
																// Dates (MM/DD/YYYY, MM/DD/YY, or MM-DD-YYYY)
																.replace(
																	/\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b/g,
																	(_, m, d, y) => {
																		const year = y.length === 2 ? `20${y}` : y;
																		const startDate = `${year}-${m.padStart(
																			2,
																			"0"
																		)}-${d.padStart(2, "0")}T09:00`; // 9AM default
																		const calendarUrl = `https://calendar.google.com/calendar/r/eventedit?dates=${startDate.replace(
																			/-/g,
																			""
																		)}/${startDate.replace(/-/g, "")}`;
																		return `<a href="${calendarUrl}" target="_blank" class="underline text-[#1DB954]">${m}/${d}/${y}</a>`;
																	}
																),
														}}
													/>
												)}
											</span>

											{/* Show date and time of the message */}
											<p className="text-xs text-gray-400 mt-1">
												{(() => {
													const date = new Date(msg.createdAt);
													const day = date.getDate();
													const month = date.toLocaleString("en-US", {
														month: "short",
													});
													const year = date.getFullYear();
													const time = date.toLocaleTimeString("en-US", {
														hour: "numeric",
														minute: "2-digit",
														hour12: true,
													});

													const getOrdinal = (n) => {
														const s = ["th", "st", "nd", "rd"];
														const v = n % 100;
														return s[(v - 20) % 10] || s[v] || s[0];
													};

													return `${day}${getOrdinal(
														day
													)} ${month} ${year}, ${time}`;
												})()}
											</p>
										</div>
									);
								})
							}
							{isTyping && <TypingIndicator />}
						</>
					)}
					{/* Scroll to the bottom of the chat when new messages arrive */}
					<div ref={messagesEndRef} />
				</div>
				{/* input for messages */}
				<MessageInput match={match} />

				{/* Modal for viewing attachments */}
				{viewAttachment && (
					<ViewAttachmentModal
						attachment={viewAttachment}
						onClose={handleCloseModal}
					/>
				)}
			</div>
		</div>
	);
};
export default ChatPage;

//shows if match not found - all styling
const MatchNotFound = () => (
	<div className="h-screen flex flex-col items-center justify-center bg-[#0b0b0b] bg-dot-pattern">
		<div className="bg-[#181818] border border-[#2a2a2a] p-8 rounded-lg shadow-md text-center">
			<UserX size={64} className="mx-auto text-[#1DB954] mb-4" />
			<h2 className="text-2xl font-semibold text-gray-100 mb-2">
				Match Not Found
			</h2>
			<p className="text-gray-400">
				Oops! It seems this match doesn&apos;t exist or has been removed.
			</p>
			<Link
				to="/"
				className="mt-6 px-4 py-2 bg-[#1DB954] text-black rounded hover:bg-[#1ed760] transition-colors 
				focus:outline-none focus:ring-2 focus:ring-[#1DB954] inline-block"
			>
				Go Back To Home
			</Link>
		</div>
	</div>
);

//shows if loading - all styling
const LoadingMessagesUI = () => (
	<div className="h-screen flex flex-col items-center justify-center bg-[#0b0b0b]">
		<div className="bg-[#181818] border border-[#2a2a2a] p-8 rounded-lg shadow-md text-center">
			<Loader size={48} className="mx-auto text-[#1DB954] animate-spin mb-4" />
			<h2 className="text-2xl font-semibold text-gray-100 mb-2">
				Loading Chat
			</h2>
			<p className="text-gray-400">
				Please wait while we fetch your conversation...
			</p>
			<div className="mt-6 flex justify-center space-x-2">
				<div
					className="w-3 h-3 bg-[#1DB954] rounded-full animate-bounce"
					style={{ animationDelay: "0s" }}
				></div>
				<div
					className="w-3 h-3 bg-[#1DB954] rounded-full animate-bounce"
					style={{ animationDelay: "0.2s" }}
				></div>
				<div
					className="w-3 h-3 bg-[#1DB954] rounded-full animate-bounce"
					style={{ animationDelay: "0.4s" }}
				></div>
			</div>
		</div>
	</div>
);

const TypingIndicator = () => (
	<div className="text-left mb-3">
		<div className="inline-block p-4 rounded-lg bg-[#1f1f1f] rounded-tl-none">
			<div className="flex space-x-1 h-3 items-center">
				<div
					className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
					style={{ animationDelay: "0s" }}
				></div>
				<div
					className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
					style={{ animationDelay: "0.2s" }}
				></div>
				<div
					className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"
					style={{ animationDelay: "0.4s" }}
				></div>
			</div>
		</div>
		<p className="text-xs text-gray-500 mt-1 ml-1">Typing...</p>
	</div>
);
