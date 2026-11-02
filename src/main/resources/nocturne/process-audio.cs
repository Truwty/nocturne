using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading;

namespace NocturneAudio {
    [ComImport, Guid("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface AudioClient {
        [PreserveSig] int Initialize(int share, uint flags, long duration, long periodicity, ref WaveFormat format, IntPtr session);
        [PreserveSig] int GetBufferSize(out uint frames);
        [PreserveSig] int GetStreamLatency(out long latency);
        [PreserveSig] int GetCurrentPadding(out uint padding);
        [PreserveSig] int IsFormatSupported(int share, IntPtr format, out IntPtr closest);
        [PreserveSig] int GetMixFormat(out IntPtr format);
        [PreserveSig] int GetDevicePeriod(out long normal, out long minimum);
        [PreserveSig] int Start();
        [PreserveSig] int Stop();
        [PreserveSig] int Reset();
        [PreserveSig] int SetEventHandle(IntPtr handle);
        [PreserveSig] int GetService(ref Guid iid, [MarshalAs(UnmanagedType.IUnknown)] out object result);
    }
    [ComImport, Guid("C8ADBD64-E71E-48A0-A4DE-185C395CD317"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface CaptureClient {
        [PreserveSig] int GetBuffer(out IntPtr data, out uint frames, out uint flags, out ulong position, out ulong clock);
        [PreserveSig] int ReleaseBuffer(uint frames);
        [PreserveSig] int GetNextPacketSize(out uint frames);
    }
    [ComImport, Guid("72A22D78-CDE4-431D-B8CC-843A71199B6D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface ActivationOperation {
        [PreserveSig] int GetActivateResult(out int result, [MarshalAs(UnmanagedType.IUnknown)] out object client);
    }
    [ComVisible(true), Guid("41D949AB-9862-444A-80F6-C261334DA5EB"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface ActivationHandler { [PreserveSig] int ActivateCompleted(ActivationOperation operation); }
    [ComVisible(true), Guid("94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    public interface Agile {}
    [StructLayout(LayoutKind.Sequential, Pack=2)]
    public struct WaveFormat {
        public ushort tag, channels;
        public uint rate, bytesPerSecond;
        public ushort blockAlign, bits, extra;
    }
    [StructLayout(LayoutKind.Sequential)]
    public struct Blob { public uint size; public IntPtr data; }
    [StructLayout(LayoutKind.Explicit)]
    public struct Variant { [FieldOffset(0)] public ushort type; [FieldOffset(8)] public Blob blob; }
    [ComVisible(true), ClassInterface(ClassInterfaceType.None)]
    public sealed class Completion : ActivationHandler, Agile {
        public readonly ManualResetEvent Ready = new ManualResetEvent(false);
        public object Client;
        public int Result;
        public IntPtr Parameters;
        public int ActivateCompleted(ActivationOperation operation) {
            try { int result; object client; Result = operation.GetActivateResult(out result, out client); if (Result >= 0) Result = result; Client = client; }
            catch (Exception ex) { Result = Marshal.GetHRForException(ex); }
            finally { if (Parameters != IntPtr.Zero) { Marshal.FreeHGlobal(Parameters); Parameters = IntPtr.Zero; } Ready.Set(); }
            return 0;
        }
    }
    public sealed class Capture : IDisposable {
        [DllImport("Mmdevapi.dll", CharSet=CharSet.Unicode, ExactSpelling=true)]
        static extern int ActivateAudioInterfaceAsync(string path, ref Guid iid, ref Variant parameters, ActivationHandler callback, out ActivationOperation operation);
        readonly object gate = new object();
        readonly short[] ring = new short[2048];
        int write, count;
        long updated;
        volatile bool stopped;
        readonly Thread thread;
        public volatile string Status = "Starting selected application capture";
        public readonly int ProcessId;
        public bool Running { get { return thread.IsAlive; } }
        public Capture(int pid) {
            if (pid <= 0) throw new ArgumentException("Invalid capture process");
            ProcessId = pid; thread = new Thread(Run); thread.IsBackground = true;
            thread.Name = "Nocturne selected-player PCM"; thread.SetApartmentState(ApartmentState.MTA); thread.Start();
        }
        void Run() {
            AudioClient client = null; CaptureClient capture = null; ActivationOperation operation = null;
            var completion = new Completion();
            try {
                completion.Parameters = Marshal.AllocHGlobal(12);
                Marshal.WriteInt32(completion.Parameters, 0, 1); // process-loopback activation
                Marshal.WriteInt32(completion.Parameters, 4, ProcessId);
                Marshal.WriteInt32(completion.Parameters, 8, 0); // include this process tree
                var parameters = new Variant { type = 65, blob = new Blob { size = 12, data = completion.Parameters } };
                Guid iid = typeof(AudioClient).GUID;
                int hr = ActivateAudioInterfaceAsync("VAD\\Process_Loopback", ref iid, ref parameters, completion, out operation);
                if (hr < 0) { Marshal.FreeHGlobal(completion.Parameters); completion.Parameters = IntPtr.Zero; Marshal.ThrowExceptionForHR(hr); }
                if (!completion.Ready.WaitOne(5000)) throw new TimeoutException("Windows application audio activation timed out");
                Marshal.ThrowExceptionForHR(completion.Result);
                client = (AudioClient)completion.Client;
                if (stopped) return;
                var format = new WaveFormat { tag = 1, channels = 2, rate = 44100, bits = 16, blockAlign = 4, bytesPerSecond = 176400 };
                Marshal.ThrowExceptionForHR(client.Initialize(0, 0x88060000, 0, 0, ref format, IntPtr.Zero));
                object service; iid = typeof(CaptureClient).GUID;
                Marshal.ThrowExceptionForHR(client.GetService(ref iid, out service)); capture = (CaptureClient)service;
                using (var ready = new AutoResetEvent(false)) {
                    Marshal.ThrowExceptionForHR(client.SetEventHandle(ready.SafeWaitHandle.DangerousGetHandle()));
                    Marshal.ThrowExceptionForHR(client.Start()); Status = "Selected application audio";
                    while (!stopped) {
                        ready.WaitOne(100);
                        uint available;
                        Marshal.ThrowExceptionForHR(capture.GetNextPacketSize(out available));
                        while (available > 0 && !stopped) {
                            IntPtr data; uint frames, flags; ulong position, clock;
                            Marshal.ThrowExceptionForHR(capture.GetBuffer(out data, out frames, out flags, out position, out clock));
                            try {
                                if (frames > 44100) throw new InvalidOperationException("Audio packet exceeded limit");
                                short[] samples = new short[frames * 2];
                                if ((flags & 2) == 0 && data != IntPtr.Zero) Marshal.Copy(data, samples, 0, samples.Length);
                                lock (gate) {
                                    for (int i = 0; i < frames; i++) { ring[write] = (short)(((int)samples[i * 2] + samples[i * 2 + 1]) / 2); write = (write + 1) % ring.Length; count = Math.Min(count + 1, ring.Length); }
                                    updated = Stopwatch.GetTimestamp();
                                }
                            } finally { Marshal.ThrowExceptionForHR(capture.ReleaseBuffer(frames)); }
                            Marshal.ThrowExceptionForHR(capture.GetNextPacketSize(out available));
                        }
                    }
                }
            } catch (Exception ex) { Status = "Application audio unavailable (" + ex.GetType().Name + ": " + ex.Message + ")"; }
            finally {
                if (client != null) { client.Stop(); Marshal.ReleaseComObject(client); }
                if (capture != null) Marshal.ReleaseComObject(capture);
                if (operation != null) Marshal.ReleaseComObject(operation);
            }
        }
        public string Snapshot() {
            lock (gate) {
                if (count < ring.Length || updated == 0 || Stopwatch.GetTimestamp() - updated > Stopwatch.Frequency / 2) return "";
                byte[] bytes = new byte[ring.Length * 2];
                for (int i = 0; i < ring.Length; i++) { short s = ring[(write + i) % ring.Length]; bytes[i * 2] = (byte)s; bytes[i * 2 + 1] = (byte)(s >> 8); }
                return Convert.ToBase64String(bytes);
            }
        }
        public void Dispose() { stopped = true; thread.Join(250); }
    }

    public static class PlayerProcess {
        [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
        struct Entry {
            public uint size, usage, id; public UIntPtr heap; public uint module, threads, parent; public int priority; public uint flags;
            [MarshalAs(UnmanagedType.ByValTStr, SizeConst=260)] public string name;
        }
        [DllImport("kernel32.dll", SetLastError=true)] static extern IntPtr CreateToolhelp32Snapshot(uint flags, uint id);
        [DllImport("kernel32.dll", CharSet=CharSet.Unicode)] static extern bool Process32FirstW(IntPtr snapshot, ref Entry entry);
        [DllImport("kernel32.dll", CharSet=CharSet.Unicode)] static extern bool Process32NextW(IntPtr snapshot, ref Entry entry);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        public static int Resolve(string app) {
            string name = null, lower = (app ?? "").ToLowerInvariant();
            string[] names = { "spotify", "cider", "tidal", "foobar2000", "youtubemusic", "chrome", "msedge", "firefox" };
            foreach (string candidate in names) if (lower.Contains(candidate)) { name = candidate + ".exe"; break; }
            if (name == null) return 0;
            IntPtr snapshot = CreateToolhelp32Snapshot(2, 0);
            if (snapshot == new IntPtr(-1)) return 0;
            try {
                var matches = new Dictionary<uint,uint>(); var entry = new Entry { size = (uint)Marshal.SizeOf(typeof(Entry)) };
                if (Process32FirstW(snapshot, ref entry)) do { if (String.Equals(entry.name, name, StringComparison.OrdinalIgnoreCase)) matches[entry.id] = entry.parent; } while (Process32NextW(snapshot, ref entry));
                int result = 0;
                foreach (var match in matches) if (!matches.ContainsKey(match.Value)) { if (result != 0) return 0; result = (int)match.Key; }
                return result;
            } finally { CloseHandle(snapshot); }
        }
    }
}
