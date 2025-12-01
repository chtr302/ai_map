import httpx
import os
import json
import sys

class BraveSearchTools:
    """
    Class để tương tác với Brave Search API, cung cấp các công cụ tìm kiếm web và tìm kiếm địa phương.
    """
    def __init__(self):
        self.api_key = os.environ.get("BRAVE_API_KEY")
        self.base_url = "https://api.search.brave.com/res/v1/web"
        
        # Xử lý trường hợp không có API Key để tránh crash httpx
        headers = {
            "Accept": "application/json",
            "Accept-Encoding": "gzip"
        }
        if self.api_key:
            headers["X-Subscription-Token"] = self.api_key
            
        self.headers = headers
        self.http_client = httpx.AsyncClient(headers=self.headers, timeout=10.0)

    async def _make_request(self, endpoint: str, params: dict) -> dict:
        """Helper function để thực hiện request tới Brave Search API."""
        if not self.api_key:
            return {"error": "BRAVE_API_KEY not found in environment variables. Please set it to use Brave Search."}

        try:
            response = await self.http_client.get(
                f"{self.base_url}/{endpoint}",
                params=params
            )
            
            if response.status_code == 401:
                return {"error": "Invalid Brave API Key. Please check your X-Subscription-Token."}
            
            response.raise_for_status() # Raises HTTPStatusError for bad responses (4xx or 5xx)
            return response.json()

        except httpx.HTTPStatusError as e:
            print(f"Brave Search HTTP error for {endpoint}: {e.response.status_code} - {e.response.text}", file=sys.stderr)
            return {"error": f"Lỗi HTTP từ Brave Search ({endpoint}): {e.response.status_code} - {e.response.text}"}
        except httpx.RequestError as e:
            print(f"Brave Search request error for {endpoint}: {e}", file=sys.stderr)
            return {"error": f"Lỗi mạng khi gọi Brave Search ({endpoint}): {str(e)}"}
        except Exception as e:
            print(f"Brave Search unexpected error for {endpoint}: {e}", file=sys.stderr)
            return {"error": f"Lỗi không xác định khi gọi Brave Search ({endpoint}): {str(e)}"}

    async def web_search(self, query: str, count: int = 5) -> list:
        """
        Tìm kiếm thông tin trên web bằng Brave Search.

        Args:
            query (str): Từ khóa tìm kiếm.
            count (int): Số lượng kết quả muốn lấy (tối đa 20).
            
        Returns:
            list: Danh sách các kết quả tìm kiếm (title, description, url).
        """
        params = {
            "q": query,
            "count": min(count, 20) # Giới hạn số lượng kết quả tối đa
        }
        data = await self._make_request("search", params)
        
        if "error" in data:
            return [data] # Trả về lỗi nếu có

        web_results = data.get("web", {}).get("results", [])
        
        simplified_results = []
        for result in web_results:
            simplified_results.append({
                "title": result.get("title"),
                "description": result.get("description"),
                "url": result.get("url"),
                "age": result.get("age", "")
            })
            
        return simplified_results

    async def local_search(self, query: str, count: int = 5, country: str = "VN", search_lang: str = "vi") -> list:
        """
        Tìm kiếm địa điểm (local businesses/POIs) bằng Brave Search.

        Args:
            query (str): Tên hoặc mô tả địa điểm cần tìm.
            count (int): Số lượng kết quả muốn lấy (tối đa 20).
            country (str): Mã quốc gia (ví dụ: "VN" cho Việt Nam).
            search_lang (str): Ngôn ngữ tìm kiếm (ví dụ: "vi" cho tiếng Việt).

        Returns:
            list: Danh sách các địa điểm tìm thấy (tên, địa chỉ, rating, giờ mở cửa, URL...).
        """
        params = {
            "q": query,
            "count": min(count, 20),
            "country": country,
            "search_lang": search_lang
        }
        data = await self._make_request("search", params)

        if "error" in data:
            return [data] # Trả về lỗi nếu có

        local_results = data.get("local", {}).get("results", [])

        simplified_results = []
        for result in local_results:
            coords = result.get("coordinates", {})
            simplified_results.append({
                "name": result.get("name"),
                "address": result.get("address"),
                "phone": result.get("phone"),
                "rating": result.get("avg_rating"),
                "total_ratings": result.get("total_ratings"),
                "url": result.get("url"),
                "website": result.get("website"),
                "hours": result.get("hours_text"),
                "lat": coords.get("latitude"),
                "lon": coords.get("longitude")
            })
        return simplified_results
