import os

EXCLUDE_DIRS = {'.git', '__pycache__', 'venv', '.streamlit'}
EXCLUDE_FILES = {'project_context.txt', '.env', 'flatten_context.py'}

def flatten_project():
    """Scans the directory and writes all relevant files into a single text document."""
    with open('project_context.txt', 'w', encoding='utf-8') as outfile:
        for root, dirs, files in os.walk('.'):
            dirs[:] = [d for d in dirs if d not in EXCLUDE_DIRS]
            for file in files:
                if file in EXCLUDE_FILES or file.endswith(('.pyc', '.png', '.jpg', '.jpeg', '.gif', '.ico')):
                    continue
                filepath = os.path.join(root, file)
                
                outfile.write(f"\n--- FILE: {filepath} ---\n")
                try:
                    with open(filepath, 'r', encoding='utf-8') as infile:
                        outfile.write(infile.read())
                except Exception as e:
                    outfile.write(f"[Error reading file {filepath}: {e}]\n")

if __name__ == '__main__':
    flatten_project()
    print("✓ Project context successfully flattened into project_context.txt!")