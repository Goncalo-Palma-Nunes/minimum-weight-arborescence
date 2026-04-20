import json
import sys
import os
import matplotlib.pyplot as plt
from math import log2

MS_TO_MIN = 1 / (1000 * 60)
BYTES_TO_MIB = 1 / (1024 * 1024)
BYTES_TO_GIB = 1 / (1024 * 1024 * 1024)

MEMORY_KEYS = ["heap_usage"]#, "non_heap_usage", "total_memory"]
RUNTIME_KEYS = ["pre_process_times", "Inference time"]

def format_memory_key(key):
    if key == "heap_usage":
        return "Heap Memory"
    if key == "non_heap_usage":
        return "Non-Heap Memory"
    if key == "total_memory":
        return "Total Memory"
    return key

def format_runtime_key(key):
    if key == "pre_process_times":
        return "Pre-processing Time"
    if key == "Inference time":
        return "Inference Time"
    return key

def detect_type(data):
    if "timestamps" in data:
        return "memory"
    if "numNodes" in data:
        return "runtime"
    return None

def logarithm(n):
    if n <= 0:
        return 0
    return log2(n)

def bytes_to_mib(bytes_value):
    return bytes_value * BYTES_TO_MIB

def bytes_to_gib(bytes_value):
    return bytes_value * BYTES_TO_GIB

def transform_x_axis(struct, numNodes_list):
    """Transform x-axis based on complexity function."""
    if struct == "pairingHeap":
        return [n * logarithm(n) + (n**2) for n in numNodes_list]
    else:  # stateless
        return [n**3 for n in numNodes_list]


def minutes_to_hours(minutes):
    return minutes / 60


def seconds_to_minutes(seconds):
    return seconds / 60


def downsample(x, y, factor):
    """Downsample data by averaging every 'factor' points."""
    if factor <= 1:
        return x, y
    x_downsampled = []
    y_downsampled = []
    for i in range(0, len(x), factor):
        x_downsampled.append(sum(x[i:i+factor]) / len(x[i:i+factor]))
        y_downsampled.append(sum(y[i:i+factor]) / len(y[i:i+factor]))
    return x_downsampled, y_downsampled


def moving_average(y, window_size):
    """Apply moving average smoothing to data."""
    if window_size <= 1:
        return y
    smoothed = []
    for i in range(len(y)):
        start = max(0, i - window_size // 2)
        end = min(len(y), i + window_size // 2 + 1)
        smoothed.append(sum(y[start:end]) / (end - start))
    return smoothed


def plot_files(json_files, output_prefix="plot", title=None, memory_keys=None, runtime_keys=None, struct="stateless", time_unit="hours", downsample_factor=1, smooth_window=1):
    if memory_keys is None:
        memory_keys = MEMORY_KEYS
    if runtime_keys is None:
        runtime_keys = RUNTIME_KEYS
    memory_files = []
    runtime_files = []

    for path in json_files:
        with open(path, "r") as f:
            data = json.load(f)
        kind = detect_type(data)
        if kind == "memory":
            memory_files.append(data)
        elif kind == "runtime":
            runtime_files.append(data)
        else:
            print(f"Warning: could not determine type for {path}, skipping.")

    if memory_files:
        fig, ax = plt.subplots(figsize=(10, 6))
        for data in memory_files:
            x = data["timestamps"]
            if time_unit == "hours":
                print(f"Converting timestamps from seconds to hours for {data['name']}")
                x = [minutes_to_hours(seconds_to_minutes(t)) for t in x]
            elif time_unit == "minutes":
                print(f"Converting timestamps from seconds to minutes for {data['name']}")
                x = [seconds_to_minutes(t) for t in x]
            name = data["name"]
            for key in memory_keys:
                if key in data:
                    y = [bytes_to_mib(v) for v in data[key]]
                    # Apply downsampling and smoothing
                    if downsample_factor > 1:
                        x, y = downsample(x, y, downsample_factor)
                    if smooth_window > 1:
                        y = moving_average(y, smooth_window)
                    ax.plot(x, y, label=f"{name}")# - {format_memory_key(key)}")
        ax.set_xlabel(f"Time ({time_unit})", fontsize=12)
        ax.set_ylabel("Memory (MiB)", fontsize=12)
        if title is not None:
            ax.set_title(title)
        ax.legend(fontsize="small", loc="upper left")
        ax.grid(True)
        fig.tight_layout()
        out = output_prefix + "_memory.png"
        fig.savefig(out, dpi=150)
        print(f"Saved {out}")
        plt.close(fig)

    if runtime_files:
        fig, ax = plt.subplots(figsize=(10, 6))
        for data in runtime_files:
            x_raw = data["numNodes"]
            x = x_raw
            if not "pre_process_times" in data:
                x = transform_x_axis(struct, x_raw)
            name = data["name"]
            for key in runtime_keys:
                if key in data:
                    y = [v * MS_TO_MIN for v in data[key]]
                    if time_unit == "hours":
                        print(f"Converting {key} from minutes to hours for {name}")
                        y = [minutes_to_hours(v) for v in y]
                    ax.plot(x, y, label=f"{name}") # - {format_runtime_key(key)}")
        xlabel = ""
        if struct == "stateless":
            xlabel = "V³"
        else:  # pairingHeap
            xlabel = "V·log(V) + V²"
        if "pre_process_times" in runtime_keys and len(runtime_keys) == 1:
            xlabel = "V"
        ax.set_xlabel(xlabel, fontsize=12)
        y_label = "Time (hours)" if time_unit == "hours" else "Time (minutes)"
        ax.set_ylabel(y_label, fontsize=12)
        if title is not None:
            ax.set_title(title)
        ax.legend(fontsize="small", loc="upper left")
        ax.grid(True)
        fig.tight_layout()
        out = output_prefix + "_runtime.png"
        fig.savefig(out, dpi=150)
        print(f"Saved {out}")
        plt.close(fig)


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 plot_results.py <file1.json> [file2.json ...] [--output PREFIX] [--title TITLE] [--metrics METRIC1 METRIC2 ...] [--struct pairingHeap|stateless] [--time hours|minutes] [--downsample N] [--smooth N]")
        sys.exit(1)

    args = sys.argv[1:]
    output_prefix = "plot"
    title = None
    memory_keys = None
    runtime_keys = None
    struct = "stateless"
    time_unit = "hours"
    downsample_factor = 1
    smooth_window = 1

    if "--output" in args:
        idx = args.index("--output")
        output_prefix = args[idx + 1]
        args = args[:idx] + args[idx + 2:]

    if "--title" in args:
        idx = args.index("--title")
        title = args[idx + 1]
        args = args[:idx] + args[idx + 2:]

    if "--metrics" in args:
        idx = args.index("--metrics")
        metrics = []
        for i in range(idx + 1, len(args)):
            if args[i].startswith("--"):
                break
            metrics.append(args[i])
        args = args[:idx] + args[idx + 1 + len(metrics):]
        memory_keys = [m for m in metrics if m in MEMORY_KEYS]
        runtime_keys = [m for m in metrics if m in RUNTIME_KEYS]

    if "--struct" in args:
        idx = args.index("--struct")
        struct = args[idx + 1]
        args = args[:idx] + args[idx + 2:]

    if "--time" in args:
        idx = args.index("--time")
        time_unit = args[idx + 1]
        if time_unit not in ["hours", "minutes"]:
            print("Error: --time must be 'hours' or 'minutes'")
            sys.exit(1)
        args = args[:idx] + args[idx + 2:]

    if "--downsample" in args:
        idx = args.index("--downsample")
        downsample_factor = int(args[idx + 1])
        args = args[:idx] + args[idx + 2:]

    if "--smooth" in args:
        idx = args.index("--smooth")
        smooth_window = int(args[idx + 1])
        args = args[:idx] + args[idx + 2:]

    plot_files(args, output_prefix=output_prefix, title=title, memory_keys=memory_keys, runtime_keys=runtime_keys, struct=struct, time_unit=time_unit, downsample_factor=downsample_factor, smooth_window=smooth_window)


if __name__ == "__main__":
    main()
