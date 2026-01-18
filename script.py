import os
import pandas as pd

def excel_sheet_to_csv(
    excel_file,
    sheet_name,
    output_dir,
    sep=",",
    encoding="utf-8"
):
    # Ensure output directory exists
    os.makedirs(output_dir, exist_ok=True)

    # Read sheet (keep first column if it's an index)
    df = pd.read_excel(excel_file, sheet_name=sheet_name)

    # Fix "Unnamed: 0" column name
    if "Unnamed: 0" in df.columns:
        if sheet_name.lower().startswith("node"):
            df = df.rename(columns={"Unnamed: 0": "Node_ids"})
        elif sheet_name.lower().startswith("task"):
            df = df.rename(columns={"Unnamed: 0": "Task_ids"})

    # Build output path
    base_name = os.path.splitext(os.path.basename(excel_file))[0]
    output_csv = os.path.join(output_dir, f"{base_name}_{sheet_name}.csv")

    # Export without index
    df.to_csv(output_csv, index=False, sep=sep, encoding=encoding)

    print(f"Sheet '{sheet_name}' exported → {output_csv}")


if __name__ == "__main__":
    numbs = [80, 120, 160, 200, 240]
    output_dir = "src/main/resources"

    for number in numbs:
        excel_file = f"task{number}.xlsx"

        excel_sheet_to_csv(excel_file, "NodeDetails", output_dir)
        excel_sheet_to_csv(excel_file, "TaskDetails", output_dir)
