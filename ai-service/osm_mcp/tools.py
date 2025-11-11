import httpx
import json, sys
import asyncio

class OsmTools:
    """
    Class để đóng gói logic thực thi cho các công cụ liên quan đến OpenStreetMap.
    """
    def __init__(self):
        self.http_client = httpx.AsyncClient(headers={"User-Agent": "AI-Map-Project/0.1"})
        self.NOMINATIM_BASE_URL = "https://nominatim.openstreetmap.org"
        self.OVERPASS_API_URL = "https://overpass-api.de/api/interpreter"

    async def geocode(self, address: str) -> dict:
        """
        Chuyển đổi một địa chỉ hoặc tên địa điểm thành tọa độ địa lý.

        Args:
            address (str): Địa chỉ hoặc tên địa điểm cần chuyển đổi (ví dụ: "Hồ Gươm, Hà Nội").

        Returns:
            dict: Một dictionary chứa 'lat', 'lon', 'display_name' và các thông tin chi tiết khác nếu tìm thấy,
                  hoặc một dictionary rỗng nếu không tìm thấy hoặc có lỗi.
        """
        SEARCH_URL = f"{self.NOMINATIM_BASE_URL}/search"
        params = {
            "q": address,
            "format": "json",
            "limit": 5,
            "addressdetails": 1,
            "extratags": 1,
            "namedetails": 1
        }
        
        try:
            response = await self.http_client.get(SEARCH_URL, params=params, timeout=30.0)
            response.raise_for_status()
            data = response.json()
            
            if data:
                best_result = None
                for result in data:
                    if result.get("importance", 0) > -1:
                        if not best_result or result.get("importance", 0) > best_result.get("importance", 0):
                            best_result = result

                if not best_result:
                    best_result = data[0]

                return {
                    "lat": float(best_result["lat"]),
                    "lon": float(best_result["lon"]),
                    "display_name": best_result["display_name"],
                    "name": best_result.get("namedetails", {}).get("name", ""),
                    "address": best_result.get("address", {}),
                    "type": best_result.get("type", ""),
                    "importance": best_result.get("importance", 0),
                    "osm_type": best_result.get("osm_type", ""),
                    "osm_id": best_result.get("osm_id", "")
                }
            return {}
        except httpx.HTTPStatusError as e:
            print(f"Geocoding error: {e.response.status_code} - {e.response.text}", file=sys.stderr)
            raise httpx.HTTPStatusError(f"Nominatim HTTP error for geocode: {e.response.status_code} - {e.response.text}", request=e.request, response=e.response) from e
        except httpx.RequestError as e:
            print(f"Geocoding request error: {e}", file=sys.stderr)
            raise httpx.RequestError(f"Nominatim request error for geocode: {e}", request=e.request) from e
        except Exception as e:
            print(f"Geocoding unexpected error: {e}", file=sys.stderr)
            raise Exception(f"Unexpected error during geocode: {e}") from e
    
    async def reverse_geocode(self, lat: float, lon: float) -> dict:
        """
        Chuyển đổi tọa độ địa lý thành một địa chỉ có thể đọc được.

        Args:
            lat (float): Vĩ độ của điểm cần chuyển đổi.
            lon (float): Kinh độ của điểm cần chuyển đổi.

        Returns:
            dict: Một dictionary chứa 'display_name' và các thông tin chi tiết khác nếu tìm thấy,
                  hoặc một dictionary rỗng nếu không tìm thấy hoặc có lỗi.
        """
        REVERSE_URL = f"{self.NOMINATIM_BASE_URL}/reverse"
        params = {
            "lat": lat,
            "lon": lon,
            "format": "json",
            "addressdetails": 1,
            "extratags": 1,
            "namedetails": 1
        }
        try:
            response = await self.http_client.get(REVERSE_URL, params=params, timeout=30.0)
            response.raise_for_status()
            data = response.json()
            if data and "display_name" in data:
                return {
                    "display_name": data["display_name"],
                    "name": data.get("namedetails", {}).get("name", ""),
                    "address": data.get("address", {}),
                    "type": data.get("type", ""),
                    "osm_type": data.get("osm_type", ""),
                    "osm_id": data.get("osm_id", "")
                }
            return {}
        except httpx.HTTPStatusError as e:
            return {"error": f"Reverse geocode failed with status {e.response.status_code}", "details": str(e)}
        except Exception as e:
            return {"error": "Reverse geocode failed", "details": str(e)}
    
    async def find_places_by_tags(self, lat: float, lon: float, radius: int, tags) -> list:
        """
        Tìm các địa điểm trong một bán kính nhất định xung quanh một tọa độ, dựa trên các thẻ OpenStreetMap.
        Hàm này sẽ tự động thực hiện reverse geocoding để lấy địa chỉ đầy đủ cho mỗi địa điểm.

        Args:
            lat (float): Vĩ độ của điểm trung tâm tìm kiếm.
            lon (float): Kinh độ của điểm trung tâm tìm kiếm.
            radius (int): Bán kính tìm kiếm tính bằng mét.
            tags (dict or str): Một dictionary chứa các cặp key-value của OpenStreetMap tags để lọc địa điểm,
                                hoặc một string đơn giản (ví dụ: "cafe") hoặc chuỗi JSON.
                                Hỗ trợ các định dạng:
                                - "cafe" (tên tiện nghi đơn giản)
                                - {"amenity": "restaurant", "cuisine": "vietnamese"} (bộ lọc phức tạp)
                                - {"amenity": "school"} (tìm trường học)
                                - {"shop": "supermarket"} (tìm siêu thị)
                                - Và các từ khóa đặc biệt như: "quán", "cà phê", "trường", "bệnh viện", v.v.

        Returns:
            list: Một danh sách các dictionary, mỗi dictionary đại diện cho một địa điểm tìm thấy đã được làm giàu thông tin địa chỉ.
        """
        if isinstance(tags, str):
            try:
                parsed_tags = json.loads(tags)
                if isinstance(parsed_tags, dict):
                    tags = parsed_tags
                else:
                    tags = {"amenity": tags}
            except json.JSONDecodeError:
                tag_str = tags.lower().strip()
                if 'quán' in tag_str or ' restaurant' in tag_str or ' nhà hàng' in tag_str:
                    tags = {"amenity": "restaurant"}
                elif 'cà phê' in tag_str or 'cafe' in tag_str or 'coffee' in tag_str:
                    tags = {"amenity": "cafe"}
                elif 'trường' in tag_str or 'school' in tag_str:
                    tags = {"amenity": "school"}
                elif 'bệnh viện' in tag_str or 'hospital' in tag_str:
                    tags = {"amenity": "hospital"}
                elif 'chợ' in tag_str or 'market' in tag_str:
                    tags = {"amenity": "marketplace"}
                elif 'siêu thị' in tag_str or 'supermarket' in tag_str:
                    tags = {"shop": "supermarket"}
                elif 'phở' in tag_str or 'bún' in tag_str or 'bánh mì' in tag_str:
                    tags = {"amenity": "restaurant", "cuisine": tag_str}
                elif 'kfc' in tag_str or 'mcdonald' in tag_str or 'hamburger' in tag_str:
                    tags = {"amenity": "fast_food", "name": tag_str}
                elif 'bệnh viện' in tag_str:
                    tags = {"amenity": "hospital"}
                elif 'chùa' in tag_str or 'chua' in tag_str:
                    tags = {"amenity": "place_of_worship", "religion": "buddhist"}
                elif 'trung tâm thương mại' in tag_str or 'mua sắm' in tag_str or 'shopping' in tag_str:
                    tags = {"shop": "mall"}
                elif 'khách sạn' in tag_str or 'hotel' in tag_str:
                    tags = {"tourism": "hotel"}
                elif 'xe máy' in tag_str or 'ô tô' in tag_str or 'gara' in tag_str:
                    tags = {"shop": "car_repair"}
                elif 'ngân hàng' in tag_str or 'bank' in tag_str:
                    tags = {"amenity": "bank"}
                elif 'quán ăn' in tag_str:
                    tags = {"amenity": "restaurant"}
                else:
                    tags = {"amenity": tags}

        if not isinstance(tags, dict):
            tags = {"amenity": str(tags) if tags else "unknown"}
        tag_filters = "".join([f'["{k}"="{str(v).replace("\"", "\\\"")}"]' for k, v in tags.items()])

        radius = int(radius)
        if radius > 5000: # Giới hạn tối đa 5km
            radius = 5000
        if radius < 100: # Giới hạn tối thiểu 100m
            radius = 100

        overpass_query = f"""
        [out:json][timeout:25];
        (
          node{tag_filters}(around:{radius},{lat},{lon});
          way{tag_filters}(around:{radius},{lat},{lon});
          relation{tag_filters}(around:{radius},{lat},{lon});
        );
        out body;
        >;
        out skel qt;
        """
        print("Generated Overpass Query:\n", overpass_query, file=sys.stderr)
        try:
            response = await self.http_client.post(self.OVERPASS_API_URL, data=overpass_query, timeout=60.0) # Tăng timeout lên 60 giây
            response.raise_for_status()
            data = response.json()
            
            elements = data.get("elements", [])
            limited_elements = elements[:20]

            initial_places = []
            for element in limited_elements:
                place_lat = element.get("lat") or (element.get("center", {}).get("lat"))
                place_lon = element.get("lon") or (element.get("center", {}).get("lon"))

                if place_lat and place_lon:
                    initial_places.append({
                        "id": element.get("id"),
                        "name": element.get("tags", {}).get("name"),
                        "lat": place_lat,
                        "lon": place_lon,
                    })

            tasks = [self._reverse_geocode_internal(p["lat"], p["lon"]) for p in initial_places]
            address_results = await asyncio.gather(*tasks)

            enriched_places = []
            for i, place in enumerate(initial_places):
                place["address"] = address_results[i]
                enriched_places.append(place)

            return enriched_places
            
        except httpx.HTTPStatusError as e:
            if e.response.status_code == 504:  # Gateway Timeout
                print(f"Overpass API timeout for query: {tag_filters} near {lat}, {lon}", file=sys.stderr)
                return [{"error": "Hệ thống tìm kiếm đang quá tải, vui lòng thử lại sau", "type": "system_overload"}]
            else:
                raise httpx.HTTPStatusError(f"Overpass HTTP error for find_places_by_tags: {e.response.status_code} - {e.response.text}", request=e.request, response=e.response) from e
        except httpx.RequestError as e:
            print(f"Request error during find_places_by_tags: {e}", file=sys.stderr)
            return [{"error": "Lỗi kết nối đến hệ thống tìm kiếm", "type": "connection_error"}]
        except Exception as e:
            print(f"Unexpected error during find_places_by_tags: {e}", file=sys.stderr)
            raise Exception(f"Unexpected error during find_places_by_tags: {e}") from e

    async def _reverse_geocode_internal(self, lat: float, lon: float) -> str:
        """
        Hàm nội bộ để lấy địa chỉ, trả về string hoặc thông báo lỗi.
        """
        REVERSE_URL = f"{self.NOMINATIM_BASE_URL}/reverse"
        params = {"lat": lat, "lon": lon, "format": "json"}
        try:
            response = await self.http_client.get(REVERSE_URL, params=params, timeout=15.0)
            response.raise_for_status()
            data = response.json()
            return data.get("display_name", "Address not found")
        except httpx.TimeoutException:
            print(f"Timeout during reverse geocoding for coordinates: {lat}, {lon}", file=sys.stderr)
            return "Address lookup timeout, using coordinates"
        except httpx.HTTPStatusError as e:
            print(f"HTTP error during reverse geocoding: {e}", file=sys.stderr)
            return "Address lookup failed due to server error"
        except Exception as e:
            print(f"Unexpected error during reverse geocoding: {e}", file=sys.stderr)
            return "Address lookup failed"