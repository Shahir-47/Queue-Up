import { useCallback, useEffect, useState } from "react";
import { Activity, Loader, Monitor, RefreshCcw, Server } from "lucide-react";
import { Header } from "../components/Header";
import { axiosInstance } from "../lib/axios";

const STATUS_STYLES = {
	ok: {
		label: "Operational",
		badge: "text-[#1DB954] bg-[#1DB954]/10 border-[#1DB954]/40",
	},
	down: {
		label: "Unavailable",
		badge: "text-rose-400 bg-rose-500/10 border-rose-500/40",
	},
	checking: {
		label: "Checking",
		badge: "text-amber-300 bg-amber-500/10 border-amber-500/40",
	},
};

const StatusBadge = ({ status }) => {
	const style = STATUS_STYLES[status] || STATUS_STYLES.checking;
	const showSpinner = status === "checking";

	return (
		<span
			className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-[0.65rem] font-semibold uppercase tracking-[0.3em] ${style.badge}`}
		>
			{showSpinner && <Loader className="h-3 w-3 animate-spin" />}
			{style.label}
		</span>
	);
};

const StatusCard = ({ icon: Icon, title, status, description }) => (
	<div className="rounded-2xl bg-[#181818] border border-[#2a2a2a] p-6 shadow-sm">
		<div className="flex items-start justify-between gap-4">
			<div className="flex items-center gap-3">
				<div className="flex h-11 w-11 items-center justify-center rounded-xl border border-[#2a2a2a] bg-[#101010]">
					<Icon className="text-[#1DB954]" size={20} />
				</div>
				<div>
					<p className="text-sm font-semibold text-gray-100">{title}</p>
					<p className="text-xs text-gray-500">{description}</p>
				</div>
			</div>
			<StatusBadge status={status} />
		</div>
	</div>
);

const HealthPage = () => {
	const [apiStatus, setApiStatus] = useState("checking");
	const [apiMessage, setApiMessage] = useState("Checking /api/health.");
	const [lastChecked, setLastChecked] = useState(null);

	const checkApi = useCallback(async () => {
		setApiStatus("checking");
		setApiMessage("Checking /api/health.");

		try {
			const res = await axiosInstance.get("/health", { timeout: 5000 });
			const message =
				typeof res.data === "string"
					? res.data
					: res.data?.message || "Backend responded to /api/health.";
			setApiStatus("ok");
			setApiMessage(message);
		} catch (error) {
			setApiStatus("down");
			if (error?.response?.status === 404) {
				setApiMessage("No /api/health endpoint found.");
			} else {
				setApiMessage("Backend did not respond.");
			}
		} finally {
			setLastChecked(new Date());
		}
	}, []);

	useEffect(() => {
		checkApi();
	}, [checkApi]);

	const lastCheckedLabel = lastChecked
		? lastChecked.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
		: "Not yet";

	return (
		<div className="min-h-screen bg-gradient-to-br from-[#0b0b0b] via-[#121212] to-[#1a1a1a] flex flex-col">
			<Header />
			<main className="flex-grow px-4 py-10">
				<div className="mx-auto flex w-full max-w-5xl flex-col gap-6">
					<section className="rounded-3xl border border-[#2a2a2a] bg-gradient-to-br from-[#1DB954]/20 via-[#181818] to-[#121212] p-6 shadow-[0_20px_50px_rgba(0,0,0,0.35)] md:p-8">
						<div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
							<div className="flex items-start gap-4">
								<div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-[#1DB954]/40 bg-[#1DB954]/15">
									<Activity className="text-[#1DB954]" size={24} />
								</div>
								<div>
									<p className="text-xs uppercase tracking-[0.35em] text-gray-400">
										System status
									</p>
									<h1 className="mt-2 text-3xl font-extrabold text-gray-100 md:text-4xl">
										Health check
									</h1>
									<p className="mt-2 text-gray-400">
										Live pulse of Queue Up core services.
									</p>
								</div>
							</div>
							<button
								type="button"
								onClick={checkApi}
								disabled={apiStatus === "checking"}
								className={`inline-flex items-center gap-2 rounded-full border px-4 py-2 text-sm font-semibold transition ${
									apiStatus === "checking"
										? "cursor-not-allowed border-[#2a2a2a] bg-[#121212] text-gray-500"
										: "border-[#1DB954]/60 bg-[#1DB954] text-black hover:bg-[#1ed760]"
								}`}
							>
								<RefreshCcw
									className={`h-4 w-4 ${
										apiStatus === "checking" ? "animate-spin" : ""
									}`}
								/>
								Run check
							</button>
						</div>
						<div className="mt-6 flex items-center gap-2 text-xs text-gray-500">
							<span>Last checked</span>
							<span className="text-gray-300">{lastCheckedLabel}</span>
						</div>
					</section>

					<div className="grid gap-4 md:grid-cols-2">
						<StatusCard
							icon={Monitor}
							title="Frontend UI"
							status="ok"
							description="Interface assets loaded and responsive."
						/>
						<StatusCard
							icon={Server}
							title="Backend API"
							status={apiStatus}
							description={apiMessage}
						/>
					</div>
				</div>
			</main>
		</div>
	);
};

export default HealthPage;
