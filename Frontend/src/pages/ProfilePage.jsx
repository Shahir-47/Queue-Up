import { useState, useRef } from "react";
import { Header } from "../components/Header";
import { useAuthStore } from "../store/useAuthStore";
import { useUserStore } from "../store/useUserStore";

const ProfilePage = () => {
	const { authUser } = useAuthStore();
	const [name, setName] = useState(authUser.name || "");
	const [bio, setBio] = useState(authUser.bio || "");
	const [age, setAge] = useState(authUser.age || "");
	const [ageValid, setAgeValid] = useState(true);
	const [image, setImage] = useState(authUser.image || null);

	const fileInputRef = useRef(null); // use this to upload an image

	const { loading, updateProfile } = useUserStore();

	// function to update the image state so we can display it and upload it to the database
	const handleImageChange = (e) => {
		const file = e.target.files[0];
		if (file) {
			const reader = new FileReader();
			reader.onloadend = () => {
				setImage(reader.result);
			};

			reader.readAsDataURL(file);
		}
	};

	const handleSubmit = (e) => {
		e.preventDefault(); // Stops page reload so we can handle form submission with JavaScript
		updateProfile({ name, bio, age, image }); // Call the update function to update user data
	}; // The updateProfile function will handle the API call and update the loading state

	return (
		<div className="min-h-screen bg-[#121212] flex flex-col">
			<Header />

			<div className="flex-grow flex flex-col justify-center py-12 px-4 sm:px-6 lg:px-8">
				<div className="sm:mx-auto sm:w-full sm:max-w-md">
					<h2 className="mt-6 text-center text-3xl font-extrabold text-gray-100">
						Your Profile
					</h2>
				</div>

				<div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md">
					<div className="bg-[#181818] py-8 px-4 shadow sm:rounded-lg sm:px-10 border border-[#2a2a2a]">
						{/* FORM */}
						<form onSubmit={handleSubmit} className="space-y-6">
							{/* NAME */}
							<div>
								<label
									htmlFor="name"
									className="block text-sm font-medium text-gray-300"
								>
									Name
								</label>
								<div className="mt-1">
									<input
										id="name"
										name="name"
										type="text"
										required
										value={name}
										maxLength={100}
										autoCapitalize="words"
										onChange={(e) => {
											const formatted = e.target.value
												.toLowerCase()
												.split(" ")
												.map(
													(word) => word.charAt(0).toUpperCase() + word.slice(1)
												)
												.join(" ");
											setName(formatted);
										}}
										className="capitalize appearance-none block w-full px-3 py-2 border border-[#2a2a2a]
										 rounded-md shadow-sm bg-[#121212] text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-[#1DB954] focus:border-[#1DB954] 
										sm:text-sm"
									/>
								</div>
							</div>

							{/* AGE */}
							<div>
								<label
									htmlFor="age"
									className="block text-sm font-medium text-gray-300"
								>
									Age
								</label>
								<div className="mt-1">
									<input
										id="age"
										name="age"
										type="number"
										required
										min="18"
										max="120"
										value={age}
										onChange={(e) => {
											const val = e.target.value;
											// Allow only digits
											if (/^\d*$/.test(val)) {
												setAge(val);

												// Validate: age must be a number between 18 and 120
												const ageNum = parseInt(val, 10);
												setAgeValid(ageNum >= 18 && ageNum <= 120);
												}
										}}
										className="appearance-none block w-full px-3 py-2 border border-[#2a2a2a] rounded-md shadow-sm bg-[#121212] text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-[#1DB954] focus:border-[#1DB954] sm:text-sm"
									/>
								</div>
								{age && !ageValid && (
									<p className="text-sm text-red-600 mt-1">
										You must be between 18 and 120 years old.
									</p>
								)}
							</div>

							{/* BIO */}
							<div>
								<label
									htmlFor="bio"
									className="block text-sm font-medium text-gray-300"
								>
									Bio
								</label>
								<div className="mt-1">
									<textarea
										id="bio"
										name="bio"
										rows={3}
										value={bio}
										onChange={(e) => setBio(e.target.value)}
										maxLength={200}
										className="appearance-none block w-full px-3 py-2 border border-[#2a2a2a] rounded-md shadow-sm bg-[#121212] text-gray-100 placeholder-gray-500 focus:outline-none focus:ring-[#1DB954] focus:border-[#1DB954] sm:text-sm"
									/>
								</div>
								<div className="text-right text-xs text-gray-400 mt-1">
									{bio.length} / 200 characters
								</div>
							</div>

							{/* PROFILE PICTURE */}
							<div>
								<label className="block text-sm font-medium text-gray-300">
									Cover Image
								</label>
								<div className="mt-1 flex items-center">
									<button
										type="button"
										onClick={() => fileInputRef.current.click()} // refers to the input below
										className="cursor-pointer inline-flex items-center px-4 py-2 border border-[#2a2a2a] rounded-md shadow-sm text-sm font-medium text-gray-200 bg-[#121212] transition-colors duration-200 hover:bg-[#1f1f1f] hover:text-[#1DB954] hover:border-[#1DB954] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#1DB954] focus:ring-offset-[#0b0b0b]"
									>
										Upload Image
									</button>
									{/* This input allows you to upload files and is used above in the onClick function of the button */}
									<input
										ref={fileInputRef}
										type="file"
										accept="image/*"
										className="hidden"
										onChange={handleImageChange} // call handleImageChange to show the newly selected photo
									/>
								</div>
							</div>

							{/* Renders the image chosen */}
							{image && (
								<div className="mt-4">
									<img
										src={image}
										alt="User Image"
										className="w-48 h-full object-cover rounded-md"
									/>
								</div>
							)}

							<button
								type="submit"
								className={`w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-black ${
									// if loading is true, show loading styles, else show normal styles
									loading
										? "bg-[#1a7f3c] cursor-not-allowed text-black"
										: "bg-[#1DB954] text-black hover:bg-[#1ed760] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#1DB954] focus:ring-offset-[#0b0b0b] cursor-pointer"
								}`}
								disabled={loading} // disable when loading
							>
								{/* if loading, show "Saving...", else show "Save" */}
								{loading ? "Saving..." : "Save"}
							</button>
						</form>
					</div>
				</div>
			</div>
		</div>
	);
};

export default ProfilePage;
