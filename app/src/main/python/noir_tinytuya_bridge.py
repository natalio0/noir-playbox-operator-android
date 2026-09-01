import json
import time

import tinytuya


def _safe_error(exc, local_key=""):
    message = str(exc)
    if local_key:
        message = message.replace(local_key, "***")
    return message


def _error_response(response, local_key=""):
    if not isinstance(response, dict):
        return None
    err_code = response.get("Err")
    err_text = response.get("Error")
    if err_code is None and err_text is None:
        return None
    payload = response.get("Payload")
    message = str(err_text or "TinyTuya device error")
    if local_key:
        message = message.replace(local_key, "***")
    return {"ok": False, "error": message, "err": str(err_code) if err_code is not None else None, "payload": payload, "raw": response}


def _make_device(device_id, ip, local_key, version, socket_timeout=5, retries=3, retry_delay=1):
    d = tinytuya.Device(str(device_id).strip(), str(ip).strip(), str(local_key))
    d.set_version(float(version))
    d.set_socketPersistent(True)
    d.set_socketTimeout(socket_timeout)
    d.set_socketRetryLimit(retries)
    d.set_socketRetryDelay(retry_delay)
    return d


def _switch_value(dps, switch_dps):
    if not isinstance(dps, dict):
        return None
    key_string = str(int(switch_dps))
    if key_string in dps:
        return dps[key_string]
    try:
        key_int = int(switch_dps)
        if key_int in dps:
            return dps[key_int]
    except Exception:
        pass
    return None


def _normalize_response(response, switch_dps, local_key=""):
    error = _error_response(response, local_key)
    if error is not None:
        return error
    response = response if isinstance(response, dict) else {"response": response}
    dps = response.get("dps") or {}
    switch = _switch_value(dps, switch_dps)
    return {"ok": True, "switch": switch if isinstance(switch, bool) else None, "dps": dps, "raw": response}


def library_info():
    crypto = "unknown"
    try:
        from Crypto.Cipher import AES  # noqa: F401
        crypto = "PyCryptodome"
    except Exception:
        pass
    return json.dumps({"ok": True, "tinytuya": getattr(tinytuya, "__version__", "unknown"), "crypto": crypto, "mode": "LAN_ONLY"})


def scan_json(seconds=12):
    """Return LAN-discovered Tuya devices as JSON. No cloud credentials required."""
    try:
        seconds = max(3, min(int(seconds), 30))
        found = tinytuya.deviceScan(False, seconds)
        devices = []
        if isinstance(found, dict):
            for address, raw in found.items():
                row = raw if isinstance(raw, dict) else {}
                devices.append({
                    "ip": str(row.get("ip") or address or ""),
                    "id": str(row.get("gwId") or row.get("id") or row.get("devId") or ""),
                    "version": str(row.get("version") or row.get("ver") or "3.3"),
                    "productKey": str(row.get("productKey") or row.get("product_key") or ""),
                    "name": str(row.get("name") or "Tuya Device"),
                })
        return json.dumps({"ok": True, "devices": devices})
    except Exception as exc:
        return json.dumps({"ok": False, "error": _safe_error(exc), "devices": []})


def status_fast_json(device_id, ip, local_key, version, switch_dps=1):
    """Low-latency presence/status probe for Home polling."""
    d = None
    try:
        d = _make_device(device_id, ip, local_key, version, socket_timeout=2, retries=1, retry_delay=0.15)
        response = d.status()
        return json.dumps(_normalize_response(response, switch_dps, local_key=local_key), default=str)
    except Exception as exc:
        return json.dumps({"ok": False, "error": _safe_error(exc, local_key)})
    finally:
        if d is not None:
            try: d.close()
            except Exception: pass


def status_json(device_id, ip, local_key, version, switch_dps=1):
    d = None
    try:
        d = _make_device(device_id, ip, local_key, version)
        response = d.status()
        return json.dumps(_normalize_response(response, switch_dps, local_key=local_key), default=str)
    except Exception as exc:
        return json.dumps({"ok": False, "error": _safe_error(exc, local_key)})
    finally:
        if d is not None:
            try: d.close()
            except Exception: pass


def set_power_json(device_id, ip, local_key, version, switch_dps, on):
    d = None
    try:
        d = _make_device(device_id, ip, local_key, version)
        command_response = d.set_status(bool(on), switch=int(switch_dps))
        command_error = _error_response(command_response, local_key)
        if command_error is not None:
            return json.dumps(command_error, default=str)
        time.sleep(0.4)
        status_response = d.status()
        result = _normalize_response(status_response, switch_dps, local_key=local_key)
        if not result.get("ok"):
            return json.dumps(result, default=str)
        result["command"] = command_response
        result["requested_on"] = bool(on)
        return json.dumps(result, default=str)
    except Exception as exc:
        return json.dumps({"ok": False, "error": _safe_error(exc, local_key)})
    finally:
        if d is not None:
            try: d.close()
            except Exception: pass
