import aiohttp
import asyncio
import time
import pandas as pd


# Server URL
webserver_ip = "localhost"  # Change to your server's IP if needed
server_url = f"http://{webserver_ip}:80/fifteenpuzzle?size=10&shuffles=104"

# Function to make a single async request
async def make_request(session, url):
    start = time.perf_counter()
    try:
        async with session.get(url) as response:
            content = await response.text()
            end = time.perf_counter()
            elapsed = end - start
            return {
                'url': url,
                'status': response.status,
                'time': elapsed,
                'content': content,
            }
    except Exception as e:
        end = time.perf_counter()
        return {
            'url': url,
            'status': 'ERROR',
            'time': end - start,
            'content': str(e),
        }

# Main function to make multiple concurrent requests
async def main():
    async with aiohttp.ClientSession() as session:
        times = []
        for _ in range(1):
            tasks = [make_request(session, server_url) for _ in range(2)]  # Adjust number of requests here
            responses = await asyncio.gather(*tasks)
            for i, response in enumerate(responses):
                times.append(response["time"])
                print(f"\n--- Response {i + 1} ---")
                print(f"Status: {response['status']}")
                print(f"Time: {response['time']:.2f} seconds")
                print(f"Content:\n{response['content']}\n")

        # Create a DataFrame from the times list
        df = pd.DataFrame(times, columns=["Response Time"])
        df.to_csv("response_times.csv", index=False)

# Entry point
if __name__ == "__main__":
    asyncio.run(main())
