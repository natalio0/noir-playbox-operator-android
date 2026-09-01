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

    return {
        "ok": False,
        "error": message,
        "err": str(err_code) if err_code is not None else None,
        "payload": payload,
        "raw": response,
    }


def _make_device(device_id, ip, local_key, version):
    # Follow TinyTuya's documented 3.5 pattern:
    # create Device -> set_version -> persistent socket.
    d = tinytuya.Device(
        str(device_id).strip(),
        str(ip).strip(),
        str(local_key),
    )
    d.set_version(float(version))
    d.set_socketPersistent(True)
    d.set_socketTimeout(5)
    d.set_socketRetryLimit(3)
    d.set_socketRetryDelay(1)
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

    return {
        "ok": True,
        "switch": switch if isinstance(switch, bool) else None,
        "dps": dps,
        "raw": response,
    }


def library_info():
    crypto = "unknown"
    try:
        from Crypto.Cipher import AES  # noqa: F401
        crypto = "PyCryptodome"
    except Exception:
        pass

    return json.dumps({
        "ok": True,
        "tinytuya": getattr(tinytuya, "__version__", "unknown"),
        "crypto": crypto,
        "mode": "LAN_ONLY",
    })


def status_json(device_id, ip, local_key, version, switch_dps=1):
    d = None
    try:
        d = _make_device(device_id, ip, local_key, version)
        response = d.status()

        result = _normalize_response(
            response,
            switch_dps,
            local_key=local_key,
        )

        return json.dumps(result, default=str)
    except Exception as exc:
        return json.dumps({
            "ok": False,
            "error": _safe_error(exc, local_key),
        })
    finally:
        if d is not None:
            try:
                d.close()
            except Exception:
                pass


def set_power_json(
    device_id,
    ip,
    local_key,
    version,
    switch_dps,
    on,
):
    d = None
    try:
        d = _make_device(device_id, ip, local_key, version)

        command_response = d.set_status(
            bool(on),
            switch=int(switch_dps),
        )

        command_error = _error_response(command_response, local_key)
        if command_error is not None:
            return json.dumps(command_error, default=str)

        # Keep the same 3.5 session/socket and verify the resulting DPS.
        time.sleep(0.4)
        status_response = d.status()

        result = _normalize_response(
            status_response,
            switch_dps,
            local_key=local_key,
        )

        if not result.get("ok"):
            return json.dumps(result, default=str)

        result["command"] = command_response
        result["requested_on"] = bool(on)

        return json.dumps(result, default=str)
    except Exception as exc:
        return json.dumps({
            "ok": False,
            "error": _safe_error(exc, local_key),
        })
    finally:
        if d is not None:
            try:
                d.close()
            except Exception:
                pass
