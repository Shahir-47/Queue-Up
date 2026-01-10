import axios from "axios";

// 1. CHANGE PORT TO 8080
const BASE_URL =
	import.meta.env.MODE === "development" ? "http://localhost:8080/api" : "/api";

export const axiosInstance = axios.create({
	baseURL: BASE_URL,
	withCredentials: true,
});

axiosInstance.interceptors.response.use((response) => {
	const transformData = (data) => {
		if (!data) return data;
		if (Array.isArray(data)) {
			return data.map(transformData);
		}
		if (typeof data === "object" && data !== null) {
			// If we see 'id' but no '_id', create '_id'
			if (data.id !== undefined && data._id === undefined) {
				data._id = data.id;
			}
			// Recursively transform nested objects
			Object.keys(data).forEach((key) => {
				data[key] = transformData(data[key]);
			});
		}
		return data;
	};

	response.data = transformData(response.data);
	return response;
});
