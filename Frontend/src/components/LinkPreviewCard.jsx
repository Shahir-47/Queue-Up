import React from "react";
import { X } from "lucide-react";

export default function LinkPreviewCard({ preview, onClose, close = false }) {
	if (!preview) return null;
	const { title, description, images, url } = preview;
	const thumb = images?.[0];
	return (
		<div className="relative mb-2 border border-[#2a2a2a] rounded-lg overflow-hidden hover:shadow-lg transition bg-[#181818]">
			{/* close button */}
			{close && (
				<button
					onClick={onClose}
					className="absolute top-1 right-1 text-gray-400 hover:text-red-400 z-10 transition-colors cursor-pointer"
				>
					<X size={16} />
				</button>
			)}
			<a href={url} target="_blank" rel="noopener noreferrer" className="flex">
				{thumb && <img src={thumb} className="w-24 object-cover" alt={title} />}
				<div className="p-2 flex-1">
					<h4 className="font-semibold text-gray-100 text-sm truncate max-w-[43rem] text-left">
						{title}
					</h4>
					{/* now rendering description */}
					{description && (
						<p className="text-gray-400 text-xs max-w-[43rem] truncate text-left">
							{description}
						</p>
					)}
					<p className="text-[#1DB954] text-xs mt-1 max-w-[43rem] truncate text-left">
						{url}
					</p>
				</div>
			</a>
		</div>
	);
}
