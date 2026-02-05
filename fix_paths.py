import os
import shutil

root_dir = 'app/src/main/java/com/hereliesaz'

for root, dirs, files in os.walk(root_dir):
    for dir_name in dirs:
        if 'blusnu.ui.' in dir_name:
            # Found a bad directory like 'blusnu.ui.dashboard'
            bad_path = os.path.join(root, dir_name)
            # The correct path should be 'blusnu/ui/dashboard' relative to 'com/hereliesaz'
            # But wait, 'blusnu' is already a directory under 'hereliesaz'.
            # The bad directory is likely sitting inside 'com/hereliesaz/'.
            # So 'com/hereliesaz/blusnu.ui.dashboard'.
            # We want to move its contents to 'com/hereliesaz/blusnu/ui/dashboard'.

            # Extract the part after 'blusnu.'
            suffix = dir_name.replace('blusnu.', '') # e.g. 'ui.dashboard'

            # The target path. We assume 'blusnu' exists.
            # We need to split 'ui.dashboard' into 'ui/dashboard'.
            target_parts = suffix.split('.')
            target_path = os.path.join(root, 'blusnu', *target_parts)

            if not os.path.exists(target_path):
                os.makedirs(target_path)

            print(f"Moving contents of {bad_path} to {target_path}")

            for filename in os.listdir(bad_path):
                src_file = os.path.join(bad_path, filename)
                dst_file = os.path.join(target_path, filename)
                shutil.move(src_file, dst_file)

            os.rmdir(bad_path)
