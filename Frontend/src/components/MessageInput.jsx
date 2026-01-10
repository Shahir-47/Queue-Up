import { useEffect, useRef, useState } from "react";
import { useMessageStore } from "../store/useMessageStore";
import { Send, Smile, Paperclip, X, Mic, StopCircle } from "lucide-react";
import EmojiPicker from "emoji-picker-react";
import PreviewAttachment from "./PreviewAttachment";
import { axiosInstance } from "../lib/axios";
import { toast } from "react-hot-toast";
import LinkPreviewCard from "./LinkPreviewCard";
import { getSocket } from "../socket/socket.client";

const MAX_ATTACHMENTS = 10;

const extractUrls = (text = "") =>
	Array.from(text.matchAll(/(https?:\/\/[^\s]+)/gi), (m) => m[1]);

const MessageInput = ({ match }) => {
	const [message, setMessage] = useState("");
	const [linkPreviews, setLinkPreviews] = useState([]); // array of {url, preview, include}
	const [showAllPreviews, setShowAllPreviews] = useState(false);
	const [attachments, setAttachments] = useState([]);
	const [showEmojiPicker, setShowEmojiPicker] = useState(false);
	const [recording, setRecording] = useState(false);
	const emojiPickerRef = useRef(null);
	const fileInputRef = useRef(null);
	const mediaRecorderRef = useRef(null);
	const recordedChunksRef = useRef([]);
	const typingTimeoutRef = useRef(null);

	const { sendMessage } = useMessageStore();

	const handleTyping = () => {
		// only send "typing" event once every 2 seconds
		if (!typingTimeoutRef.current) {
			try {
				const socket = getSocket();

				// --- Check if socket is OPEN (readyState === 1) ---
				if (socket.raw && socket.raw.readyState === 1) {
					socket.raw.send(
						JSON.stringify({
							type: "typing",
							payload: { receiverId: match._id },
						})
					);
				}

				// Prevent sending again for 2 seconds
				typingTimeoutRef.current = setTimeout(() => {
					typingTimeoutRef.current = null;
				}, 2000);
			} catch (err) {
				console.log("Socket not connected", err);
			}
		}
	};

	const handleSendMessage = (e) => {
		e.preventDefault(); // so it doesn't refresh the page

		// if the message is empty and no file is selected, do nothing
		if (!message.trim() && attachments.length === 0) {
			return;
		}

		if (attachments.length > MAX_ATTACHMENTS) {
			toast.error(
				`You have attached ${attachments.length} files, but only ${MAX_ATTACHMENTS} are allowed.`
			);
			return;
		}

		const previewUrls = linkPreviews.filter((p) => p.include).map((p) => p.url);

		sendMessage(match._id, message, attachments, previewUrls); // send message to the backend
		setMessage(""); // empties message input after previous message is sent
		setAttachments([]); // empties attachments after previous message is sent
		setLinkPreviews([]); // empties link previews after previous message is sent
		setShowAllPreviews(false); // resets the state of link previews
	};

	const handleFileChange = async (e) => {
		const files = Array.from(e.target.files);
		const availableSlots = MAX_ATTACHMENTS - attachments.length;

		if (availableSlots <= 0) {
			toast.error(`Only up to ${MAX_ATTACHMENTS} attachments are allowed.`);
			e.target.value = "";
			return;
		}
		if (files.length > availableSlots) {
			toast.error(`Only ${availableSlots} more file(s) can be added.`);
		}

		const toUpload = files.slice(0, availableSlots);

		for (const file of toUpload) {
			const name = file.name;
			const ext = name.includes(".") ? name.split(".").pop().toLowerCase() : "";
			let category;

			if (file.type.startsWith("image/")) category = "image";
			else if (file.type.startsWith("video/")) category = "video";
			else if (file.type.startsWith("audio/")) category = "audio";
			else if (ext === "pdf") category = "pdf";
			else if (["xls", "xlsx", "csv"].includes(ext)) category = "spreadsheet";
			else if (["ppt", "pptx"].includes(ext)) category = "presentation";
			else if (["doc", "docx"].includes(ext)) category = "word";
			else if (["zip", "rar", "7z", "tar", "gz"].includes(ext))
				category = "archive";
			else category = "other";

			// media → Cloudinary via base64
			if (["image", "video", "audio"].includes(category)) {
				const reader = new FileReader();
				reader.onloadend = () => {
					setAttachments((prev) => [
						...prev,
						{ data: reader.result, name, ext, category },
					]);
				};
				reader.readAsDataURL(file);
			} else {
				try {
					// get presigned URL + key from server
					const { data } = await axiosInstance.post("/uploads/s3/presign", {
						name,
						type: file.type,
					});
					const { url, key } = data;

					// PUT the file to S3
					await fetch(url, {
						method: "PUT",
						body: file,
						headers: { "Content-Type": file.type },
					});

					// build the public URL
					const publicUrl = `https://${import.meta.env.VITE_S3_BUCKET}.s3.${
						import.meta.env.VITE_AWS_REGION
					}.amazonaws.com/${key}`;

					// push into attachments
					setAttachments((prev) => [
						...prev,
						{ url: publicUrl, key, name, ext, category },
					]);
				} catch (err) {
					console.error("S3 upload failed", err);
					toast.error("Could not upload file. Please try again.");
				}
			}
		}
		e.target.value = "";
	};

	const removeAttachment = (idx) => {
		setAttachments((prev) => prev.filter((_, i) => i !== idx));
	};

	// START / STOP voice recording
	const toggleRecording = async () => {
		if (recording) {
			// stop recording
			mediaRecorderRef.current.stop();
			setRecording(false);
		} else {
			// request mic permission & start
			const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
			mediaRecorderRef.current = new MediaRecorder(stream);
			recordedChunksRef.current = [];

			mediaRecorderRef.current.ondataavailable = (e) => {
				if (e.data.size > 0) recordedChunksRef.current.push(e.data);
			};
			mediaRecorderRef.current.onstop = () => {
				const blob = new Blob(recordedChunksRef.current, {
					type: "audio/webm",
				});
				const reader = new FileReader();
				reader.onloadend = () => {
					setAttachments((prev) => [
						...prev,
						{
							data: reader.result, // base64 data-url
							name: `voice-${Date.now()}.webm`,
							ext: "webm",
							category: "audio",
						},
					]);
				};
				reader.readAsDataURL(blob);
				// stop all mic tracks
				stream.getTracks().forEach((t) => t.stop());
			};

			mediaRecorderRef.current.start();
			setRecording(true);
		}
	};

	useEffect(() => {
		const urls = extractUrls(message);
		if (urls.length === 0) {
			setLinkPreviews([]);
			setShowAllPreviews(false);
			return;
		}

		// fetch all previews
		Promise.all(
			urls.map((url) =>
				axiosInstance
					.post("/link-preview", { url })
					.then((r) => ({ url, preview: r.data }))
					.catch(() => null)
			)
		).then((results) => {
			const valid = results.filter((r) => r && r.preview);
			setLinkPreviews(valid.map((r) => ({ ...r, include: true })));
			setShowAllPreviews(false);
		});
	}, [message]);

	// to exit the emoji picker
	useEffect(() => {
		// Function to handle clicks outside the emoji picker
		const handleClickOutside = (event) => {
			if (
				emojiPickerRef.current &&
				!emojiPickerRef.current.contains(event.target)
			) {
				setShowEmojiPicker(false); // close the emoji picker if clicked outside
			}
		};

		// Add event listener to detect clicks outside the emoji picker
		document.addEventListener("mousedown", handleClickOutside);

		// Clean up by removing the event listener when the component unmounts.
		return () => {
			document.removeEventListener("mousedown", handleClickOutside);
		};
	}, []);

	//styling
	return (
		<div className="w-full">
			{/* Live counter note */}
			{/* Show counter only when there's at least one attachment */}
			{attachments.length > 0 && (
				<div className="text-sm text-gray-400 mb-1">
					{attachments.length}/{MAX_ATTACHMENTS} file
					{attachments.length !== 1 ? "s" : ""} attached
					{attachments.length > MAX_ATTACHMENTS
						? ` (exceeded by ${attachments.length - MAX_ATTACHMENTS})`
						: ""}
				</div>
			)}
			{attachments.length > 0 && (
				<div className="mb-2 flex items-center space-x-2 overflow-x-auto p-2 pt-5 bg-[#1f1f1f] border border-[#2a2a2a] rounded-md shadow">
					{attachments.map((att, idx) => (
						<div
							key={idx}
							className={
								att.category === "audio"
									? "relative flex-shrink-0"
									: `relative flex-shrink-0 bg-[#121212] border border-[#2a2a2a] p-1 rounded-md ${
											["image", "video"].includes(att.category)
												? "h-32 flex items-end justify-center"
												: "h-12 flex items-center space-x-2"
									  }`
							}
						>
							<PreviewAttachment attachment={att} />
							<button
								onClick={() => removeAttachment(idx)}
								className={
									att.category === "audio"
										? "absolute bg-[#121212] border border-[#2a2a2a] rounded-full p-1 shadow-md text-red-500 hover:bg-[#1f1f1f] z-10 top-0 right-[-4px] transform translate-x-1 -translate-y-2/6 cursor-pointer"
										: "absolute bg-[#121212] border border-[#2a2a2a] rounded-full p-1 shadow-md text-red-500 hover:bg-[#1f1f1f] z-10 top-0 right-[-4px] transform translate-x-1 -translate-y-2/3 cursor-pointer"
								}
							>
								<X size={16} />
							</button>
						</div>
					))}
				</div>
			)}

			{linkPreviews.length > 0 && (
				<div className="mb-2">
					<div className="bg-[#181818] border border-[#2a2a2a] rounded max-h-33 overflow-y-auto p-2 space-y-2 relative">
						{(showAllPreviews ? linkPreviews : linkPreviews.slice(0, 1)).map(
							({ url, preview }, i) => (
								<LinkPreviewCard
									key={url + i}
									close={true}
									preview={preview}
									onClose={() =>
										setLinkPreviews((prev) =>
											prev.filter((_, idx) => idx !== i)
										)
									}
								/>
							)
						)}

						{/* now INSIDE the scroll box */}
						{!showAllPreviews && linkPreviews.length > 1 && (
							<p
								className="sticky bottom-0 bg-[#181818] text-center text-sm text-[#1DB954] cursor-pointer hover:underline py-1"
								onClick={() => setShowAllPreviews(true)}
							>
								Show {linkPreviews.length - 1} more link
								{linkPreviews.length - 1 > 1 ? "s" : ""}
							</p>
						)}
					</div>
				</div>
			)}

			<form onSubmit={handleSendMessage} className="flex relative">
				{/* button for emojis */}
				<button
					type="button"
					onClick={() => setShowEmojiPicker(!showEmojiPicker)} // toggle emoji picker
					className="cursor-pointer absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-[#1DB954] focus:outline-none"
				>
					<Smile size={24} />
				</button>

				{/* File Attachment Button */}
				<button
					type="button"
					onClick={() => fileInputRef.current.click()}
					disabled={attachments.length >= MAX_ATTACHMENTS}
					className={
						`absolute left-12 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-[#1DB954] focus:outline-none ` +
						(attachments.length >= MAX_ATTACHMENTS
							? "cursor-not-allowed opacity-50"
							: "cursor-pointer")
					}
				>
					<Paperclip size={20} />
				</button>

				{/* Voice Recording Toggle */}
				<button
					type="button"
					onClick={toggleRecording}
					className="cursor-pointer absolute left-19 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-[#1DB954] focus:outline-none"
				>
					{recording ? <StopCircle size={20} /> : <Mic size={20} />}
				</button>

				<input
					ref={fileInputRef}
					type="file"
					accept="*/*"
					multiple
					className="hidden"
					onChange={handleFileChange}
				/>

				{/* input for messages */}
				<input
					type="text"
					value={message}
					onChange={(e) => {
						setMessage(e.target.value);
						handleTyping();
					}}
					className="flex-grow p-3 pl-28 rounded-l-lg border-2 border-[#1DB954] bg-[#0f0f0f] text-gray-100 placeholder-gray-500 
        focus:outline-none focus:ring-2 focus:ring-[#1DB954]"
					placeholder="Type a message..."
				/>

				{/* button to send message */}
				<button
					type="submit"
					className="bg-[#1DB954] text-black p-3 rounded-r-lg 
        hover:bg-[#1ed760] transition-colors focus:outline-none focus:ring-2 focus:ring-[#1DB954]"
				>
					<Send size={24} />
				</button>

				{/* Emoji picker */}
				{showEmojiPicker && (
					<div ref={emojiPickerRef} className="absolute bottom-20 left-4">
						<EmojiPicker
							theme="dark"
							style={{
								"--epr-bg-color": "#0f0f0f",
								"--epr-category-label-bg-color": "#0f0f0f",
								"--epr-picker-border-color": "#2a2a2a",
							}}
							onEmojiClick={(emojiObject) => {
								// when an emoji is clicked, it adds the emoji to the message input
								setMessage((prevMessage) => prevMessage + emojiObject.emoji);
							}}
						/>
					</div>
				)}
			</form>
		</div>
	);
};
export default MessageInput;
