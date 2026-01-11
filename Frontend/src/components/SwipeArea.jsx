import TinderCard from "react-tinder-card";
import { useMatchStore } from "../store/useMatchStore";
import SharedChipsWithModal from "./SharedChipsWithModal";

const SwipeArea = () => {
	const { userProfiles, swipeRight, swipeLeft } = useMatchStore();

	const handleSwipe = (dir, user) => {
		if (dir === "right") swipeRight(user);
		else if (dir === "left") swipeLeft(user);
	};

	return (
		<div className="relative w-full max-w-sm h-[32rem] shadow-lg">
			{userProfiles.map((user) => (
				<TinderCard
					className="absolute shadow-none rounded-lg"
					key={user._id}
					onSwipe={(dir) => handleSwipe(dir, user)} // dir gives the direction
					swipeRequirementType="position" // swipe will be triggered relative to the cards position
					swipeThreshold={100}
					preventSwipe={["up", "down"]} // dont allow to swipe up or down
				>
					<div
						className="card swipe-card bg-[#181818] w-90 sm:w-96 h-[32rem] select-none rounded-lg overflow-hidden border border-[#2a2a2a]"
					>
						{/* IMAGE */}
						<figure className="px-4 pt-4 h-3/4">
							<img
								src={user.image || "/avatar.png"}
								alt={user.name}
								className="rounded-lg object-cover h-full pointer-events-none"
							/>
						</figure>

						<div className="card-body bg-gradient-to-b from-[#181818] to-[#121212]">
							{/* NAME, AGE */}
							<h2 className="card-title text-2xl text-gray-100">
								{user.name}, {user.age}
							</h2>

							{/* BIO */}
							<p className="text-gray-400">{user.bio}</p>

							{/* Shared Artists (pink) */}
							<SharedChipsWithModal
								items={user.commonArtists}
								icon="🎵"
								title="Shared Artists"
								bg="pink"
								limit={2}
								spotifyType="artist"
							/>

							{/* Shared Tracks (blue) */}
							<SharedChipsWithModal
								items={user.commonTracks}
								icon="🎶"
								title="Shared Tracks"
								bg="blue"
								limit={2}
								spotifyType="track"
							/>

							{/* Both Saved (green) */}
							<SharedChipsWithModal
								items={user.commonSaved}
								icon="💾"
								title="Both Saved Tracks"
								bg="green"
								limit={2}
								spotifyType="track"
							/>

							{/* Shared Followed (purple) */}
							<SharedChipsWithModal
								items={user.commonFollowed}
								icon="⭐"
								title="Followed Artists"
								bg="purple"
								limit={2}
								spotifyType="artist"
							/>
						</div>
					</div>
				</TinderCard>
			))}
		</div>
	);
};

export default SwipeArea;
