import json
import sys
import os
import matplotlib.pyplot as plt
from math import log2

MS_TO_MIN = 1 / (1000 * 60)
BYTES_TO_MIB = 1 / (1024 * 1024)

MEMORY_KEYS = ["heap_usage"]#, "non_heap_usage", "total_memory"]
RUNTIME_KEYS = ["pre_process_times", "Inference time"]


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

def transform_x_axis(struct, numNodes_list):
    """Transform x-axis based on complexity function."""
    if struct == "pairingHeap":
        return [n * logarithm(n) + (n**2) * logarithm(n**2) for n in numNodes_list]
    else:  # stateless
        return [n**3 for n in numNodes_list]


def plot_files(json_files, output_prefix="plot", title=None, memory_keys=None, runtime_keys=None, struct="stateless"):
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
            name = data["name"]
            for key in memory_keys:
                if key in data:
                    y = [v * BYTES_TO_MIB for v in data[key]]
                    ax.plot(x, y, label=f"{name}")# {key}")
        ax.set_xlabel("Timestamp (s)")
        ax.set_ylabel("Memory (MiB)")
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
            x = transform_x_axis(struct, x_raw)
            name = data["name"]
            for key in runtime_keys:
                if key in data:
                    y = [v * MS_TO_MIN for v in data[key]]
                    ax.plot(x, y, label=f"{name} {key}")
        xlabel = f"Number of nodes (iteration)" if struct == "stateless" else f"Complexity ({struct})"
        ax.set_xlabel(xlabel)
        ax.set_ylabel("Time (minutes)")
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
        print("Usage: python3 plot_results.py <file1.json> [file2.json ...] [--output PREFIX] [--title TITLE] [--metrics METRIC1 METRIC2 ...] [--struct pairingHeap|stateless]")
        sys.exit(1)

    args = sys.argv[1:]
    output_prefix = "plot"
    title = None
    memory_keys = None
    runtime_keys = None
    struct = "stateless"

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

    plot_files(args, output_prefix=output_prefix, title=title, memory_keys=memory_keys, runtime_keys=runtime_keys, struct=struct)


if __name__ == "__main__":
    main()
