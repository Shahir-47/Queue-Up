import { Route, Routes, Navigate } from "react-router-dom";
import HomePage from "./pages/HomePage";
import AuthPage from "./pages/AuthPage";
import ProfilePage from "./pages/ProfilePage";
import ChatPage from "./pages/ChatPage";
import HealthPage from "./pages/HealthPage";
import ErrorPage from "./pages/ErrorPage";
import { useAuthStore } from "./store/useAuthStore";
import { useEffect } from "react";
import { Toaster } from "react-hot-toast";

function App() {
	const {
		checkAuth,
		authUser,
		checkingAuth,
		subscribeToOnlineUsers,
		unsubscribeFromOnlineUsers,
	} = useAuthStore();

	useEffect(() => {
		checkAuth();
	}, [checkAuth]);

	// Manage online status subscription
	useEffect(() => {
		if (authUser) {
			subscribeToOnlineUsers();
			return () => unsubscribeFromOnlineUsers();
		}
	}, [authUser, subscribeToOnlineUsers, unsubscribeFromOnlineUsers]);

	if (checkingAuth) {
		return null;
	}

	return (
		<div className="absolute inset-0 -z-10 h-full w-full bg-[#0b0b0b] bg-[linear-gradient(to_right,#1a1a1a_1px,transparent_1px),linear-gradient(to_bottom,#1a1a1a_1px,transparent_1px)] bg-[size:6rem_4rem]">
			<Routes>
				<Route
					path="/"
					element={authUser ? <HomePage /> : <Navigate to={"/auth"} />}
				/>
				<Route
					path="/auth"
					element={!authUser ? <AuthPage /> : <Navigate to={"/"} />}
				/>
				<Route
					path="/profile"
					element={authUser ? <ProfilePage /> : <Navigate to={"/"} />}
				/>
				<Route
					path="/chat/:id"
					element={authUser ? <ChatPage /> : <Navigate to={"/"} />}
				/>
				<Route path="/health" element={<HealthPage />} />
				<Route path="*" element={<ErrorPage />} />
			</Routes>
			<Toaster />
		</div>
	);
}

export default App;
