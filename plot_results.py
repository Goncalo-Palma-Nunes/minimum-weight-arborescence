import json
import sys
import os
import matplotlib.pyplot as plt

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


def plot_files(json_files, output_prefix="plot", title=None):
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
            for key in MEMORY_KEYS:
                if key in data:
                    y = [v * BYTES_TO_MIB for v in data[key]]
                    ax.plot(x, y, label=f"{name} {key}")
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
            x = data["numNodes"]
            name = data["name"]
            for key in RUNTIME_KEYS:
                if key in data:
                    y = [v * MS_TO_MIN for v in data[key]]
                    ax.plot(x, y, label=f"{name} {key}")
        ax.set_xlabel("Number of nodes (iteration)")
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
        print("Usage: python3 plot_results.py <file1.json> [file2.json ...] [--output PREFIX] [--title TITLE]")
        sys.exit(1)

    args = sys.argv[1:]
    output_prefix = "plot"
    title = None

    if "--output" in args:
        idx = args.index("--output")
        output_prefix = args[idx + 1]
        args = args[:idx] + args[idx + 2:]

    if "--title" in args:
        idx = args.index("--title")
        title = args[idx + 1]
        args = args[:idx] + args[idx + 2:]

    plot_files(args, output_prefix=output_prefix, title=title)


if __name__ == "__main__":
    main()
