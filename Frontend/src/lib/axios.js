import axios from "axios";

export const axiosInstance = axios.create({
	baseURL: "/api",
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
