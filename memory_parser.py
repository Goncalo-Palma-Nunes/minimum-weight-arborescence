import json
import os
import sys


def parse_memory_file(filepath):
    name = os.path.basename(filepath)

    timestamps = []
    heap_usage = []
    non_heap_usage = []
    total_memory = []

    with open(filepath, "r") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            parts = line.split(",")
            timestamps.append(int(parts[0]))
            heap_usage.append(int(parts[1]))
            non_heap_usage.append(int(parts[2]))
            total_memory.append(int(parts[3]))

    return {
        "name": name,
        "timestamps": timestamps,
        "heap_usage": heap_usage,
        "non_heap_usage": non_heap_usage,
        "total_memory": total_memory,
    }


def main():
    if len(sys.argv) < 2:
        print("Usage: python3 memory_parser.py <memory_file> [output.json]")
        sys.exit(1)

    memory_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else os.path.splitext(memory_file)[0] + ".json"

    data = parse_memory_file(memory_file)

    with open(output_file, "w") as f:
        json.dump(data, f, indent=2)

    print(f"Parsed {len(data['timestamps'])} entries -> {output_file}")


if __name__ == "__main__":
    main()
