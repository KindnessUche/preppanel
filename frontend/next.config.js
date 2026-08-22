/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: "standalone", // produces a self-contained server.js for the Docker image
};

module.exports = nextConfig;
