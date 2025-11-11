import asyncio
from mcp.server import Server
from mcp.server.stdio import stdio_server
from osm_mcp import setup_tools
from mcp.server.models import InitializationOptions
from mcp.types import ServerCapabilities

async def main():
    app = Server("OsmMCP")
    setup_tools(app)

    async with stdio_server() as (read_stream, write_stream):
        await app.run(
            read_stream,
            write_stream,
            initialization_options=InitializationOptions(
                server_name="OsmMCP",
                server_version="2.12.03",
                capabilities=ServerCapabilities()
            )
        )

if __name__ == "__main__":
    asyncio.run(main())