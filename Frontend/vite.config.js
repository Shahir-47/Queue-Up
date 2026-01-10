import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import path from "path";

export default defineConfig({
	envDir: path.resolve(path.dirname(new URL(import.meta.url).pathname), ".."),

	envPrefix: ["VITE_"],

	plugins: [react(), tailwindcss()],
});
