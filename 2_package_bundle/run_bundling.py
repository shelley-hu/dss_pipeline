import dataiku
import sys
from datetime import datetime

host = sys.argv[1]
apiKey = sys.argv[2]
project = sys.argv[3]
bundle_id = sys.argv[4]

dataiku.set_remote_dss(host, apiKey)
client = dataiku.api_client()
test_project = client.get_project(project)

test_project.export_bundle(bundle_id)
test_project.download_exported_bundle_archive_to_file(bundle_id, bundle_id + ".zip")
