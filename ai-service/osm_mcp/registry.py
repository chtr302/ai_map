import json, traceback
from typing import List
from mcp.server import Server
from mcp.types import Tool, TextContent
from .tools import OsmTools
from .brave_search import BraveSearchTools # Import BraveSearchTools

def setup_tools(app: Server):
    """
    Hàm cài đặt, nơi các decorator của app được sử dụng để đăng ký tool.
    """
    osm_tools = OsmTools()
    brave_tools = BraveSearchTools()
    @app.list_tools()
    async def list_osm_tools() -> List[Tool]:
        """Trả về danh sách các tool được định nghĩa thủ công."""
        tools_list = [
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
                description="Tìm các địa điểm trong một bán kính nhất định xung quanh một tọa độ, dựa trên một từ khóa hoặc chuỗi mô tả (tags). Ví dụ: 'quán ăn Hàn Quốc', 'cafe', 'trường học'.",
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
                            "type": "string",
                            "description": "Một từ khóa hoặc chuỗi mô tả các tags của địa điểm cần tìm. Ví dụ: 'quán ăn Hàn Quốc', 'cafe', 'trường học'."
                        }
                    },
                    "required": ["lat", "lon", "radius", "tags"]
                }
            ),
            # Brave Search Tools
            Tool(
                name="web_search",
                description="Tìm kiếm thông tin tổng quát trên Internet sử dụng Brave Search. Rất hữu ích để tìm kiếm thông tin bổ sung về các địa điểm (đánh giá, thông tin liên hệ, menu, bài viết, tin tức) hoặc các chủ đề không liên quan đến địa lý cụ thể. Không dùng để tìm địa chỉ cụ thể mà nên dùng local_search nếu có thể.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "Từ khóa tìm kiếm."
                        },
                        "count": {
                            "type": "integer",
                            "description": "Số lượng kết quả muốn lấy (mặc định 5, tối đa 20)."
                        }
                    },
                    "required": ["query"]
                }
            ),
            Tool(
                name="local_search",
                description="Tìm kiếm các địa điểm cụ thể (quán ăn, cửa hàng, điểm quan tâm) bằng Brave Search. Có thể cung cấp thông tin như địa chỉ, rating, giờ mở cửa. Dùng tool này khi cần tìm thông tin địa điểm có cấu trúc. Tham số 'country' mặc định là 'ALL' vì Brave Search không hỗ trợ tìm kiếm theo quốc gia 'VN'. Luôn ưu tiên dùng tool này để tìm kiếm quán ăn, cửa hàng, và các địa điểm có review/rating.",
                inputSchema={
                    "type": "object",
                    "properties": {
                        "query": {
                            "type": "string",
                            "description": "Tên hoặc mô tả địa điểm cần tìm."
                        },
                        "count": {
                            "type": "integer",
                            "description": "Số lượng kết quả muốn lấy (mặc định 5, tối đa 20)."
                        },
                        "country": {
                            "type": "string",
                            "description": "Mã quốc gia (ví dụ: 'US', 'GB', 'ALL'). Mặc định là 'ALL'. Brave Search không hỗ trợ 'VN' nên sẽ tự động chuyển thành 'ALL'."
                        },
                        "search_lang": {
                            "type": "string",
                            "description": "Ngôn ngữ tìm kiếm (ví dụ: 'vi'). Mặc định là 'vi'."
                        }
                    },
                    "required": ["query"]
                }
            )
        ]
        return tools_list

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
            elif name == "web_search": # Brave Search Tool
                result_data = await brave_tools.web_search(**arguments)
            elif name == "local_search": # Brave Local Search Tool
                result_data = await brave_tools.local_search(**arguments)
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
