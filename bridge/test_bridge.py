import asyncio
import unittest
from unittest.mock import AsyncMock, Mock

from nocturne_bridge import Bridge, parse_command


class ProtocolTests(unittest.TestCase):
    def test_rejects_untrusted_commands(self) -> None:
        for value in ['{}', '[]', '{"action":"shell"}', '{"action":"select","source":1}',
                      '{"action":"seek","seconds":-1}', '{"action":"seek","seconds":NaN}',
                      '{"action":"seek","seconds":true}', 'x' * 4097]:
            with self.subTest(value=value[:60]), self.assertRaises(ValueError):
                parse_command(value)

    def test_accepts_seek_and_transport(self) -> None:
        self.assertEqual(parse_command('{"action":"seek","seconds":42.5}')["seconds"], 42.5)
        self.assertEqual(parse_command('{"action":"toggle"}')["action"], "toggle")

    def test_missing_selected_session_does_not_control_another_app(self) -> None:
        manager = Mock()
        manager.get_sessions.return_value = []
        bridge = Bridge(manager)
        bridge.selected = "missing.app"
        self.assertIsNone(bridge.session())
        manager.get_current_session.assert_not_called()

    def test_rejected_control_is_reported(self) -> None:
        session = Mock()
        session.try_skip_next_async = AsyncMock(return_value=False)
        manager = Mock()
        manager.get_current_session.return_value = session
        manager.get_sessions.return_value = [session]
        bridge = Bridge(manager)
        asyncio.run(bridge.command({"action": "next"}))
        self.assertIn("declined", bridge.error)

    def test_no_session_is_a_connected_idle_state(self) -> None:
        manager = Mock()
        manager.get_sessions.return_value = []
        manager.get_current_session.return_value = None
        result = asyncio.run(Bridge(manager).snapshot())
        self.assertTrue(result["connected"])
        self.assertEqual(result["sources"], [])


if __name__ == "__main__":
    unittest.main()
