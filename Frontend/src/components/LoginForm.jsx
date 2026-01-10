import { useState } from "react";
import { useAuthStore } from "../store/useAuthStore"; // import the auth store file to use the login function and loading state
import { Eye, EyeOff } from "lucide-react"; // for toggle icon

const LoginForm = () => {
	const [email, setEmail] = useState("");
	const [password, setPassword] = useState("");
	const [showPassword, setShowPassword] = useState(false); // toggle password visibility

	const { login, loading } = useAuthStore(); // get login function and loading state from the auth store file

	return (
		<form
			className="space-y-6"
			onSubmit={(e) => {
				e.preventDefault(); // Stops page reload so we can handle form submission with JavaScript
				login({ email, password }); // Call the login function from the auth store with the form data
				// The login function will handle the API call and update the loading state
			}}
		>
			{/* Email input field */}
			<div>
				<label
					htmlFor="email"
					className="block text-sm font-medium text-gray-300"
				>
					Email address
				</label>
				<div className="mt-1">
					<input
						id="email"
						name="email"
						type="email"
						autoComplete="email"
						required
						value={email}
						onChange={(e) => setEmail(e.target.value)}
						className="appearance-none block w-full px-3 py-2 border border-[#2a2a2a] rounded-md shadow-sm bg-[#121212] text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-[#1DB954] focus:border-[#1DB954] sm:text-sm"
					/>
				</div>
			</div>

			{/* Password input field */}
			<div>
				<label
					htmlFor="password"
					className="block text-sm font-medium text-gray-300"
				>
					Password
				</label>
				<div className="mt-1 relative">
					<input
						id="password"
						name="password"
						type={showPassword ? "text" : "password"}
						autoComplete="current-password"
						required
						value={password}
						onChange={(e) => setPassword(e.target.value)}
						className="appearance-none block w-full px-3 py-2 border border-[#2a2a2a] rounded-md shadow-sm bg-[#121212] text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-[#1DB954] focus:border-[#1DB954] sm:text-sm"
					/>
					<div
						onClick={() => setShowPassword(!showPassword)}
						className="absolute inset-y-0 right-0 flex items-center px-3 cursor-pointer text-gray-400 hover:text-[#1DB954] transition-colors duration-200"
					>
						{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
					</div>
				</div>
			</div>

			{/* Sign in button */}
			<button
				type="submit"
				className={`w-full flex justify-center py-2 px-4 border border-transparent 
					rounded-md shadow-sm text-sm font-medium text-black ${
						// if loading is true, show loading styles, else show normal styles
						loading
							? "bg-[#1a7f3c] cursor-not-allowed"
							: "bg-[#1DB954] cursor-pointer hover:bg-[#1ed760] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#1DB954] focus:ring-offset-[#0b0b0b]"
					}`}
				disabled={loading} // disable the button if loading is true
			>
				{/* if loading is true, show "Signing in...", else show "Sign in" */}
				{loading ? "Signing in..." : "Sign in"}
			</button>
		</form>
	);
};

export default LoginForm;
