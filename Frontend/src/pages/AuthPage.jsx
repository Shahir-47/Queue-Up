import { useState } from "react";
import LoginForm from "../components/LoginForm";
import SignUpForm from "../components/SignUpForm";

const AuthPage = () => {
	const [isLogin, setIsLogin] = useState(true); // State to track if the user is on the login or signup page

	return (
		<div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-[#0b0b0b] via-[#121212] to-[#1a1a1a] p-4">
			<div className="w-full max-w-md">
				<div className="flex flex-col items-center text-center mb-8">
					<div className="rounded-2xl bg-gradient-to-br from-[#1DB954] via-[#1a7f3c] to-transparent p-[1px] shadow-[0_12px_30px_rgba(0,0,0,0.35)]">
						<div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-[#0f0f0f]">
							<img
								src="/audio-lines.svg"
								alt="Queue Up logo"
								className="h-8 w-8"
							/>
						</div>
					</div>
					<p className="mt-4 text-xs uppercase tracking-[0.35em] text-gray-400">
						Queue Up
					</p>
					<h2 className="mt-2 text-3xl font-extrabold text-gray-100">
						{/* if on login page, show "Sign in to Queue Up", else show "Create a Queue Up account" */}
						{isLogin
							? "Sign in to Queue Up"
							: "Create a Queue Up account"}
					</h2>
					<p className="mt-2 text-sm text-gray-400">
						{isLogin
							? "Meet through music"
							: "Meet through music"}
					</p>
				</div>

				<div className="bg-[#181818] shadow-xl rounded-lg p-8 border border-[#2a2a2a]">
					{/* if on login page, show LoginForm, else show SignUpForm */}
					{isLogin ? <LoginForm /> : <SignUpForm />}

					<div className="mt-8 text-center">
						<p className="text-sm text-gray-400">
							{/* if on login page, show "New to Queue Up?", else show "Already have an account?" */}
							{isLogin
								? "New to Queue Up?"
								: "Already have an account?"}
						</p>
						<button
							className="mt-2 text-[#1DB954] hover:text-[#1ed760] font-medium transition-colors duration-300 cursor-pointer"
							// if user is on the login page, set is login to false so that the signup form is shown when the button is clicked,
							// else if user is on the signup page, set is login to true so that the login form is shown when the button is clicked
							onClick={() => setIsLogin((prevIsLogin) => !prevIsLogin)}
						>
							{/* if on login page, show "Create a new account" on the button, else show "Sign in to your account" */}
							{isLogin ? "Create a new account" : "Sign in to your account"}
						</button>
					</div>
				</div>
			</div>
		</div>
	);
};

export default AuthPage;
