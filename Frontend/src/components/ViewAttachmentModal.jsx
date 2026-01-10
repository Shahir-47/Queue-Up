import React, { useEffect, useState } from "react";
import {
	X,
	Download,
	Maximize2,
	Minimize2,
	ChevronRight,
	ChevronDown,
} from "lucide-react";
import {
	FaFileAlt,
	FaFile,
	FaFileImage,
	FaFilePdf,
	FaFileWord,
	FaFileExcel,
	FaFilePowerpoint,
	FaFileArchive,
} from "react-icons/fa";
import { axiosInstance } from "../lib/axios";
import JSZip from "jszip";
import Papa from "papaparse";
import DataTable from "react-data-table-component";

// This is used to display the contents of a zip file
const DirectoryNode = ({ node }) => {
	const [collapsed, setCollapsed] = useState(false);
	const hasChildren = node.children && node.children.length > 0;
	return (
		<div className="ml-4">
			<div
				className={`flex items-center ${hasChildren ? "cursor-pointer" : ""}`}
				onClick={() => hasChildren && setCollapsed(!collapsed)}
			>
				{hasChildren ? (
					collapsed ? (
						<ChevronRight className="mr-1" />
					) : (
						<ChevronDown className="mr-1" />
					)
				) : (
					<FaFile className="text-gray-400 mr-1" />
				)}
				<span className="text-gray-300">{node.name}</span>
			</div>
			{!collapsed && hasChildren && (
				<div>
					{node.children.map((c) => (
						<DirectoryNode key={c.path} node={c} />
					))}
				</div>
			)}
		</div>
	);
};

const ViewAttachmentModal = ({ attachment, onClose }) => {
	const [fullScreen, setFullScreen] = useState(false);
	const [loading, setLoading] = useState(true);
	const [error, setError] = useState(null);

	const [rawSrc, setRawSrc] = useState(null);
	const [publicUrl, setPublicUrl] = useState(null);
	const [zipTree, setZipTree] = useState(null);
	const [textData, setTextData] = useState("");
	const [csvData, setCsvData] = useState([]); // array of row‑objects
	const [csvCols, setCsvCols] = useState([]); // DataTable column defs

	// map file types to icons
	const getFileIcon = (ext) => {
		if (/(jpg|jpeg|png|gif|bmp|svg)/i.test(ext))
			return <FaFileImage className="text-2xl mr-2" />;
		if (/pdf/i.test(ext)) return <FaFilePdf className="text-2xl mr-2" />;
		if (/docx?/i.test(ext)) return <FaFileWord className="text-2xl mr-2" />;
		if (/xlsx?/i.test(ext)) return <FaFileExcel className="text-2xl mr-2" />;
		if (/pptx?/i.test(ext))
			return <FaFilePowerpoint className="text-2xl mr-2" />;
		if (/zip/i.test(ext)) return <FaFileArchive className="text-2xl mr-2" />;
		return <FaFileAlt className="text-2xl mr-2" />;
	};

	const toggleFullScreen = () => {
		setFullScreen((prev) => !prev);
	};

	useEffect(() => {
		let isCancelled = false;
		let blobUrl = null;
		setLoading(true);
		setError(null);
		setZipTree(null);
		setTextData("");

		// build a tree structure from the zip file
		const buildTree = (files) => {
			const root = { name: "/", children: [] };
			Object.keys(files).forEach((path) => {
				const parts = path.split("/").filter(Boolean);
				let node = root;
				parts.forEach((p, i) => {
					let child = node.children.find((c) => c.name === p);
					if (!child) {
						child = {
							name: p,
							path: parts.slice(0, i + 1).join("/"),
							children: [],
						};
						node.children.push(child);
					}
					node = child;
				});
			});
			return root;
		};

		// fetch the file and determine how to render it
		// if it's a zip, text, or font, handle it differently
		// otherwise, just set the src to the public URL
		(async () => {
			try {
				const isS3 = attachment.url?.includes(
					`${import.meta.env.VITE_S3_BUCKET}.s3.`
				);

				// if S3, get a presigned URL for download
				if (isS3 && attachment.key) {
					const ext = (attachment.ext || "").toLowerCase();
					const isOfficeFile = [
						"doc",
						"docx",
						"xls",
						"xlsx",
						"ppt",
						"pptx",
					].includes(ext);

					const { data } = await axiosInstance.post(
						"/uploads/s3/presign-download",
						{
							key: attachment.key,
							expiresIn: isOfficeFile ? 900 : 60, // 15 minutes for Office files, 1 min for others
						}
					);

					setPublicUrl(data.url);

					const blob = await fetch(data.url).then((r) => r.blob());
					blobUrl = URL.createObjectURL(blob);
					setRawSrc(blobUrl);
				} else {
					// if not S3, use the URL directly
					setPublicUrl(attachment.url || attachment.data);
					setRawSrc(attachment.url || attachment.data);
				}

				const ext = (attachment.ext || "").toLowerCase();

				// handle different file types
				if (ext === "zip") {
					const buf = await fetch(blobUrl || rawSrc).then((r) =>
						r.arrayBuffer()
					);
					const zip = await JSZip.loadAsync(buf);
					if (isCancelled) return;
					setZipTree(buildTree(zip.files));
					setLoading(false);
					return;
				}
				if (ext === "txt" || ext === "html") {
					const text = await fetch(blobUrl || rawSrc).then((r) => r.text());
					if (isCancelled) return;
					setTextData(text);
					setLoading(false);
					return;
				}
				if (ext === "csv") {
					const csvText = await fetch(blobUrl || rawSrc).then((r) => r.text());
					if (isCancelled) return;

					// Papa Parse into an array of objects (header row → keys)
					const { data } = Papa.parse(csvText, {
						header: true,
						skipEmptyLines: true,
					});

					setCsvData(data);

					// build columns for react-data-table-component
					if (data.length > 0) {
						const cols = Object.keys(data[0]).map((key) => ({
							name: key,
							selector: (row) => row[key],
							sortable: true,
						}));
						setCsvCols(cols);
					}

					setLoading(false);
					return;
				}

				// all Office handled by embed
				if (["doc", "docx", "xls", "xlsx", "ppt", "pptx"].includes(ext)) {
					setLoading(false);
					return;
				}
				setLoading(false);
			} catch (e) {
				console.error(e);
				if (!isCancelled) {
					setError("Could not render preview; please download instead.");
					setLoading(false);
				}
			}
		})();

		return () => {
			isCancelled = true;
			if (blobUrl) URL.revokeObjectURL(blobUrl);
		};
	}, [attachment]);

	const handleDownload = async () => {
		const url = rawSrc || publicUrl;
		let downloadUrl = url;

		// for images, fetch a blob so the browser downloads it
		if (attachment.category === "image") {
			const res = await fetch(url);
			let blob = await res.blob();

			if ((attachment.ext || "").toLowerCase() === "svg") {
				blob = new Blob([blob], { type: "image/svg+xml" });
			}

			downloadUrl = URL.createObjectURL(blob);
		}

		const a = document.createElement("a");
		a.href = downloadUrl;
		a.download = attachment.name;
		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);

		// clean up if created a blob URL
		if (downloadUrl.startsWith("blob:")) {
			URL.revokeObjectURL(downloadUrl);
		}
	};

	const renderContent = () => {
		if (loading)
			return (
				<div className="flex flex-col items-center justify-center h-full p-4">
					<div className="animate-spin h-12 w-12 border-b-2 border-[#1DB954] rounded-full mb-3" />
					<span className="text-gray-400">Loading preview…</span>
				</div>
			);
		if (error)
			return (
				<div className="flex flex-col items-center justify-center h-full p-4">
					<p className="text-red-500 mb-4">{error}</p>
					<button
						onClick={handleDownload}
						className="flex items-center space-x-2 bg-[#1DB954] hover:bg-[#1ed760] text-black px-4 py-2 rounded"
					>
						<Download />
						<span>Download</span>
					</button>
				</div>
			);

		const ext = (attachment.ext || "").toLowerCase();
		const u = rawSrc || publicUrl;

		if (attachment.category === "image") {
			return (
				<img
					src={u}
					alt={attachment.name}
					className={`mx-auto rounded-lg object-contain transition-all ${
						fullScreen ? "max-h-[95vh] max-w-[95vw]" : "max-h-[60vh] max-w-full"
					}`}
				/>
			);
		}
		if (ext === "txt" || ext === "html") {
			return (
				<pre
					className={`bg-[#121212] text-gray-200 border border-[#2a2a2a] rounded shadow-inner overflow-auto ${
						fullScreen ? "max-h-full" : "max-h-[80vh]"
					}`}
				>
					{textData}
				</pre>
			);
		}
		if (ext === "pdf") {
			return (
				<iframe
					src={`${u}#toolbar=1&navpanes=1&scrollbar=1`}
					className={`w-full ${
						fullScreen ? "h-full" : "h-[80vh]"
					} rounded shadow`}
					title={attachment.name}
				/>
			);
		}
		if (ext === "zip") {
			return (
				<div className="overflow-auto p-2 max-h-[70vh] bg-[#121212] border border-[#2a2a2a] rounded shadow-inner">
					{zipTree && <DirectoryNode node={zipTree} />}
				</div>
			);
		}
		// third‑party CSV grid
		if (ext === "csv") {
			return (
				<div className="max-h-[80vh] overflow-auto">
					<DataTable
						columns={csvCols}
						data={csvData}
						pagination
						dense
						persistTableHead
					/>
				</div>
			);
		}

		if (["doc", "docx", "xls", "xlsx", "ppt", "pptx"].includes(ext)) {
			return (
				<iframe
					src={
						"https://view.officeapps.live.com/op/embed.aspx?src=" +
						encodeURIComponent(publicUrl)
					}
					className={`w-full ${
						fullScreen ? "h-full" : "h-[80vh]"
					} rounded shadow`}
					frameBorder="0"
					title={attachment.name}
				/>
			);
		}
		// fallback
		return (
			<div className="flex flex-col items-center justify-center h-full p-8 text-center">
				<FaFileAlt className="text-5xl text-gray-400 mb-4" />
				<p className="text-gray-400 mb-2">No preview available.</p>
				<button
					onClick={handleDownload}
					className="text-[#1DB954] underline hover:text-[#1ed760] cursor-pointer transition hover:underline"
				>
					Download instead
				</button>
			</div>
		);
	};

	const ext = (attachment.ext || "").toLowerCase();
	return (
		<div className="fixed inset-0 z-50 flex items-center justify-center p-4">
			<div
				className="absolute inset-0 bg-black/70 backdrop-blur-sm"
				onClick={onClose}
			/>
			<div
				className={`${
					fullScreen
						? "w-screen h-screen m-0"
						: "w-full max-w-4xl max-h-[90vh] m-auto"
				} relative bg-[#121212] border border-[#2a2a2a] rounded-lg shadow-2xl overflow-hidden flex flex-col transition-all duration-300`}
			>
				<div className="flex items-center space-x-2 bg-[#181818] border-b border-[#2a2a2a] px-4 py-2">
					{getFileIcon(ext)}
					<h3 className="flex-1 text-lg font-medium text-gray-100 truncate">
						{attachment.name}
					</h3>
					<div className="flex items-center space-x-1">
						<button
							onClick={toggleFullScreen}
							className="p-1 hover:bg-[#2a2a2a] rounded cursor-pointer"
							title={fullScreen ? "Exit Full Screen" : "Full Screen"}
						>
							{fullScreen ? <Minimize2 /> : <Maximize2 />}
						</button>
						{ext !== "pdf" && (
							<button
								onClick={handleDownload}
								className="p-1 hover:bg-[#2a2a2a] rounded cursor-pointer"
								title="Download"
							>
								<Download />
							</button>
						)}
						<button
							onClick={onClose}
							className="p-1 hover:bg-[#2a2a2a] rounded cursor-pointer hover:text-red-500"
							title="Close"
						>
							<X />
						</button>
					</div>
				</div>
				<div className="flex-grow overflow-auto bg-[#121212] p-4">
					{renderContent()}
				</div>
			</div>
		</div>
	);
};

export default ViewAttachmentModal;
