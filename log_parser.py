import re
import json
import os
import sys


def parse_log_file(filepath):
    name = os.path.basename(filepath)

    pre_process_times = []
    inference_times = []
    num_nodes = []

    iteration_pattern = re.compile(
        r"Iteration\s+(\d+).*?Pre-process time \(ms\):\s*(+?\d+)"
    )
    inference_pattern = re.compile(r"Inference time \(ms\):\s*(+?\d+)")

    with open(filepath, "r") as f:
        lines = f.readlines()

    i = 0
    while i < len(lines):
        line = lines[i]
        m = iteration_pattern.search(line)
        if m:
            num_nodes.append(int(m.group(1)))
            pre_process_times.append(int(m.group(2)))
            # Inference time may be on the same line or the next
            inf_match = inference_pattern.search(line)
            if not inf_match and i + 1 < len(lines):
                inf_match = inference_pattern.search(lines[i + 1])
                if inf_match:
                    i += 1
            if inf_match:
                inference_times.append(int(inf_match.group(1)))
        i += 1

    return {
        "name": name,
        "pre_process_times": pre_process_times,
        "Inference time": inference_times,
        "numNodes": num_nodes,
    }


def main():
    if len(sys.argv) < 2:
        print("Usage: python log_parser.py <log_file> [output.json]")
        sys.exit(1)

    log_file = sys.argv[1]
    output_file = sys.argv[2] if len(sys.argv) > 2 else os.path.splitext(log_file)[0] + ".json"

    data = parse_log_file(log_file)

    with open(output_file, "w") as f:
        json.dump(data, f, indent=2)

    print(f"Parsed {len(data['numNodes'])} entries -> {output_file}")


if __name__ == "__main__":
    main()
