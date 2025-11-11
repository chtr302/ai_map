import json, traceback
from typing import List
from mcp.server import Server
from mcp.types import Tool, TextContent
from .tools import OsmTools

def setup_tools(app: Server):
    """
    Hàm cài đặt, nơi các decorator của app được sử dụng để đăng ký tool.
    """
    osm_tools = OsmTools()

    @app.list_tools()
    async def list_osm_tools() -> List[Tool]:
        """Trả về danh sách các tool được định nghĩa thủ công."""
        return [
            Tool(
                name="geocode",
                description="Chuyển đổi một địa chỉ hoặc tên địa điểm thành tọa độ địa lý (kinh độ, vĩ độ).",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "address": {
                            "type": "string",
                            "description": "Địa chỉ hoặc tên địa điểm cần chuyển đổi (ví dụ: 'Hồ Gươm, Hà Nội')."
                        }
                    },
                    "required": ["address"]
                }
            ),
            Tool(
                name="reverse_geocode",
                description="Chuyển đổi tọa độ địa lý thành một địa chỉ có thể đọc được.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "lat": {
                            "type": "number",
                            "description": "Vĩ độ của điểm cần chuyển đổi."
                        },
                        "lon": {
                            "type": "number",
                            "description": "Kinh độ của điểm cần chuyển đổi."
                        }
                    },
                    "required": ["lat", "lon"]
                }
            ),
            Tool(
                name="find_places_by_tags",
                description="Tìm các địa điểm trong một bán kính nhất định xung quanh một tọa độ, dựa trên một bộ các thẻ (tags) của OpenStreetMap. Ví dụ: để tìm quán ăn Hàn Quốc, tags có thể là {\"amenity\": \"restaurant\", \"cuisine\": \"korean\"}.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "lat": {
                            "type": "number",
                            "description": "Vĩ độ của điểm trung tâm tìm kiếm."
                        },
                        "lon": {
                            "type": "number",
                            "description": "Kinh độ của điểm trung tâm tìm kiếm."
                        },
                        "radius": {
                            "type": "integer",
                            "description": "Bán kính tìm kiếm tính bằng mét."
                        },
                        "tags": {
                            "type": "object",
                            "description": "Một dictionary chứa các cặp key-value của OpenStreetMap tags để lọc địa điểm. Ví dụ: {\"amenity\": \"restaurant\", \"cuisine\": \"korean\"}"
                        }
                    },
                    "required": ["lat", "lon", "radius", "tags"]
                }
            )
        ]

    @app.call_tool()
    async def call_osm_tool(name: str, arguments: dict) -> List[TextContent]:
        """Hàm điều phối, được gọi mỗi khi AI muốn sử dụng một tool."""
        try:
            result_data = None
            if name == "geocode":
                result_data = await osm_tools.geocode(**arguments)
            elif name == "reverse_geocode":
                result_data = await osm_tools.reverse_geocode(**arguments)
            elif name == "find_places_by_tags":
                result_data = await osm_tools.find_places_by_tags(**arguments)
            else:
                result_data = {"error": f"Không tìm thấy tool nào có tên là '{name}'."}
            
            result_text = json.dumps(result_data, indent=2, ensure_ascii=False)
            return [TextContent(type="text", text=result_text)]
        except Exception as e:
            error_info = {
                "error": f"Lỗi khi thực thi tool '{name}'",
                "details": str(e),
                "traceback": traceback.format_exc()
            }
            return [TextContent(type="text", text=json.dumps(error_info, indent=2, ensure_ascii=False))]
