// components/PreviewAttachment.jsx
import React from "react";
import {
	FaFilePdf,
	FaFileWord,
	FaFileExcel,
	FaFilePowerpoint,
	FaFileArchive,
	FaFileAlt,
} from "react-icons/fa";

export default function PreviewAttachment({ attachment, onClick }) {
	const src = attachment.data || attachment.url;
	const { category, name, ext } = attachment;

	switch (category) {
		case "image":
			return (
				<div className="w-50 h-[7.38rem] bg-[#1f1f1f] rounded-sm overflow-hidden">
					<img
						src={src}
						alt={name}
						onClick={onClick}
						className="w-full h-full object-contain cursor-pointer"
					/>
				</div>
			);
		case "video":
			return (
				<div className="w-50 h-32 rounded-sm overflow-hidden">
					<video
						src={src}
						controls
						onClick={onClick}
						className="w-full h-full object-contain"
					/>
				</div>
			);
		case "audio":
			return (
				<audio src={src} controls onClick={onClick} className="mt-1 w-100" />
			);
		case "pdf":
			return (
				<div
					onClick={onClick}
					className="flex items-center space-x-2 cursor-pointer text-left"
				>
					<FaFilePdf size={32} className="text-gray-200" />
					<div className="w-38">
						<div className="font-medium truncate text-gray-100">{name}</div>
						<div className="text-sm text-gray-400">PDF Document</div>
					</div>
				</div>
			);
		case "spreadsheet":
			return (
				<div
					onClick={onClick}
					className="flex items-center space-x-2 cursor-pointer text-left"
				>
					<FaFileExcel size={32} className="text-gray-200" />
					<div className="w-38">
						<div className="font-medium truncate text-gray-100">{name}</div>
						<div className="text-sm text-gray-400">
							{ext === "csv" ? "CSV File" : "Excel Spreadsheet"}
						</div>
					</div>
				</div>
			);
		case "presentation":
			return (
				<div
					onClick={onClick}
					className="flex items-center space-x-2 cursor-pointer text-left"
				>
					<FaFilePowerpoint size={32} className="text-gray-200" />
					<div className="w-38">
						<div className="font-medium truncate text-gray-100">{name}</div>
						<div className="text-sm text-gray-400">PowerPoint</div>
					</div>
				</div>
			);
		case "word":
			return (
				<div
					onClick={onClick}
					className="flex items-center space-x-2 cursor-pointer text-left"
				>
					<FaFileWord size={32} className="text-gray-200" />
					<div className="w-38">
						<div className="font-medium truncate text-gray-100">{name}</div>
						<div className="text-sm text-gray-400">Word Document</div>
					</div>
				</div>
			);
		case "archive":
			return (
				<div
					onClick={onClick}
					className="flex items-center space-x-2 cursor-pointer text-left"
				>
					<FaFileArchive size={32} className="text-gray-200" />
					<div className="w-38">
						<div className="font-medium truncate text-gray-100">{name}</div>
						<div className="text-sm text-gray-400">Archive File</div>
					</div>
				</div>
			);
		default:
			return (
				<div
					onClick={onClick}
					className="flex items-center space-x-2 cursor-pointer text-left"
				>
					<FaFileAlt size={32} className="text-gray-200" />
					<div className="w-38">
						<div className="font-medium truncate text-gray-100">{name}</div>
						<div className="text-sm text-gray-400">
							{ext.toUpperCase()} File
						</div>
					</div>
				</div>
			);
	}
}
