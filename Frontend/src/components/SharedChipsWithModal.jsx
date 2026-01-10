// components/SharedChipsWithModal.jsx
import { useState } from "react";
import { X } from "lucide-react";

const COLOR_VARIANTS = {
	pink: {
		bg: "bg-[#241018] border border-[#ff5f9e]/40",
		text: "text-[#ff8fc1]",
		hoverBg: "hover:bg-[#2f1420]",
	},
	blue: {
		bg: "bg-[#0f1b2a] border border-[#4da3ff]/40",
		text: "text-[#7cc4ff]",
		hoverBg: "hover:bg-[#142236]",
	},
	green: {
		bg: "bg-[#10241a] border border-[#38d97a]/45",
		text: "text-[#6df2a8]",
		hoverBg: "hover:bg-[#153023]",
	},
	purple: {
		bg: "bg-[#1a1430] border border-[#9a7bff]/40",
		text: "text-[#c1a7ff]",
		hoverBg: "hover:bg-[#231c3b]",
	},
	gray: {
		bg: "bg-[#1a1a1a] border border-[#2a2a2a]",
		text: "text-gray-200",
		hoverBg: "hover:bg-[#242424]",
	},
};

export default function SharedChipsWithModal({
	items = [],
	icon,
	title,
	bg = "gray",
	limit = 2,
	spotifyType = "track", // either "track" or "artist"
}) {
	const [openList, setOpenList] = useState(false);
	const [embedOpen, setEmbedOpen] = useState(false);
	const [selectedItem, setSelectedItem] = useState(null);
	const [embedLoaded, setEmbedLoaded] = useState(false);

	const {
		bg: bgClass,
		text: textClass,
		hoverBg,
	} = COLOR_VARIANTS[bg] || COLOR_VARIANTS.gray;

	const filtered = items.filter((it) => {
		const n = it.name.toLowerCase();
		return n;
	});

	const visible = filtered.slice(0, limit);
	const moreCount = filtered.length - visible.length;

	function openEmbed(item) {
		setEmbedLoaded(false);
		setSelectedItem(item);
		setEmbedOpen(true);
	}

	function closeEmbed() {
		setEmbedLoaded(false);
		setEmbedOpen(false);
		setSelectedItem(null);
	}

	return (
		<div className="mt-0.5">
			{/* inline chips */}
			<p className="text-sm text-gray-400 mb-0.75">
				{title} ({filtered.length})
			</p>
			<div className="flex flex-wrap gap-2">
				{visible.map((it) => (
					<span
						key={it.id}
						onClick={() => openEmbed(it)}
						className={`flex items-center space-x-1 px-3 py-1 rounded-full ${bgClass} ${textClass} text-xs cursor-pointer ${hoverBg}`}
					>
						<span>{icon}</span>
						<span className="block max-w-[4.75rem] truncate">{it.name}</span>
					</span>
				))}

				{moreCount > 0 && (
					<button
						onClick={() => setOpenList(true)}
						className={`flex items-center space-x-1 px-3 py-1 rounded-full ${bgClass} ${textClass} text-xs cursor-pointer ${hoverBg}`}
					>
						<span>+{moreCount} more</span>
					</button>
				)}
			</div>

			{/* list-modal */}
			{openList && (
				<div
					className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
					onClick={() => setOpenList(false)}
				>
					<div
						className="bg-[#181818] p-6 rounded-lg max-w-xs w-full border border-[#1DB954] shadow-lg"
						onClick={(e) => e.stopPropagation()}
					>
						<div className="flex align-items-center mb-4">
							<h3 className="text-lg font-semibold text-gray-100">{title}</h3>
							<button
								onClick={() => setOpenList(false)}
								className="ml-auto text-black hover:text-black transition-colors cursor-pointer bg-[#1DB954] rounded-full py-0 px-1.5 hover:bg-[#1ed760]"
							>
								<X size={16} />
							</button>
						</div>

						{/* list of items */}
						<div className="space-y-2 max-h-60 overflow-y-auto">
							{filtered.map((it) => (
								<div
									key={it.id}
									onClick={() => {
										setOpenList(false);
										openEmbed(it);
									}}
									className={`flex items-center space-x-2 px-3 py-2 rounded-md ${bgClass} ${textClass} cursor-pointer ${hoverBg}`}
								>
									<span>{icon}</span>
									<span className="block truncate">{it.name}</span>
								</div>
							))}
						</div>
					</div>
				</div>
			)}

			{/* spotify-embed modal */}
			{embedOpen && selectedItem && (
				<div
					className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
					onClick={closeEmbed}
				>
					<div
						className="p-0 rounded-xl shadow-lg "
						onClick={(e) => e.stopPropagation()}
					>
						{/* close button */}
						<button
							onClick={closeEmbed}
							className="absolute top-12 right-5 text-black hover:text-black z-10 transition-colors cursor-pointer bg-[#1DB954] rounded-full p-1 hover:bg-[#1ed760]"
						>
							<X size={16} />
						</button>

						{/* spotify embed */}
						<div className="relative rounded-xl overflow-hidden bg-[#121212] border border-[#2a2a2a]">
							<iframe
								src={`https://open.spotify.com/embed/${spotifyType}/${selectedItem.id}?theme=0`}
								width="300"
								height="380"
								frameBorder="0"
								allow="encrypted-media"
								title={selectedItem.name}
								onLoad={() => setEmbedLoaded(true)}
								className={`block bg-[#121212] -m-[2px] transition-opacity duration-200 ${
									embedLoaded ? "opacity-100" : "opacity-0"
								}`}
							></iframe>
							<div
								aria-hidden="true"
								className={`absolute inset-0 z-10 bg-[#121212] transition-opacity duration-200 ${
									embedLoaded ? "opacity-0 pointer-events-none" : "opacity-100"
								}`}
							/>
						</div>
					</div>
				</div>
			)}
		</div>
	);
}
