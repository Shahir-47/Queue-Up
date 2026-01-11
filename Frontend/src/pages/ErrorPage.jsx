import { AlertTriangle, ArrowLeft } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { Header } from "../components/Header";

const ErrorPage = () => {
	const navigate = useNavigate();

	return (
		<div className="min-h-screen bg-gradient-to-br from-[#0b0b0b] via-[#121212] to-[#1a1a1a] flex flex-col">
			<Header />
			<main className="flex-grow flex items-center justify-center px-4 py-12">
				<div className="w-full max-w-3xl">
					<div className="rounded-3xl bg-gradient-to-br from-[#1DB954] via-[#1a7f3c] to-transparent p-[1px] shadow-[0_20px_60px_rgba(0,0,0,0.45)]">
						<div className="rounded-3xl bg-[#181818] border border-[#2a2a2a] p-8 md:p-12">
							<div className="flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
								<div>
									<p className="text-xs uppercase tracking-[0.35em] text-gray-400">
										Queue Up
									</p>
									<h1 className="mt-3 text-4xl md:text-5xl font-extrabold text-gray-100">
										We lost that beat.
									</h1>
									<p className="mt-3 text-gray-400 max-w-xl">
										The page you are looking for does not exist or has moved.
									</p>
								</div>
								<div className="flex items-center gap-4">
									<div className="rounded-2xl bg-[#1DB954]/15 border border-[#1DB954]/40 p-4">
										<AlertTriangle className="text-[#1DB954]" size={32} />
									</div>
									<div className="text-6xl font-black text-[#1DB954]/20">
										404
									</div>
								</div>
							</div>

							<div className="mt-8 flex flex-wrap gap-3">
								<Link
									to="/"
									className="inline-flex items-center rounded-full bg-[#1DB954] px-5 py-2 text-sm font-semibold text-black transition hover:bg-[#1ed760]"
								>
									Back home
								</Link>
								<button
									type="button"
									onClick={() => navigate(-1)}
									className="inline-flex items-center gap-2 rounded-full border border-[#2a2a2a] bg-[#121212] px-5 py-2 text-sm font-semibold text-gray-200 transition hover:border-[#1DB954]/60 hover:text-[#1DB954]"
								>
									<ArrowLeft className="h-4 w-4" />
									Go back
								</button>
								<Link
									to="/health"
									className="inline-flex items-center rounded-full border border-[#2a2a2a] bg-[#121212] px-5 py-2 text-sm font-semibold text-gray-200 transition hover:border-[#1DB954]/60 hover:text-[#1DB954]"
								>
									System health
								</Link>
							</div>

							<div className="mt-8 flex items-center justify-between border-t border-[#1f1f1f] pt-4 text-xs text-gray-500">
								<span>Error code</span>
								<span className="text-gray-300 font-semibold tracking-[0.3em]">
									404
								</span>
							</div>
						</div>
					</div>
				</div>
			</main>
		</div>
	);
};

export default ErrorPage;
