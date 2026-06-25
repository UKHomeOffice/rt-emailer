#!/usr/bin/env bash

[[ ${DEBUG} == 'true' ]] && set -x
set -o errexit
set -o pipefail
set -o nounset

source bin/env.sh
export KUBE_CERTIFICATE_AUTHORITY=https://raw.githubusercontent.com/UKHomeOffice/acp-ca/master/${CLUSTER}.crt

if [ $# -lt 1 ] || [ $# -gt 2 ]; then
   echo "==> ERROR: Incorrect number of parameters"
    echo "===> Usage: $0 <image_version> [ service ]"
   exit 1
fi

########## VARIABLES ##########

export IMAGE_VERSION="$1"
SERVICE_TO_DEPLOY="${2:-rt-emailer}"

declare -a resource_types=( \
"configmap" \
"networkpolicy" \
"service" \
"deployment" \
)

########## MAIN ##########

echo "==> Namespace: ${KUBE_NAMESPACE}"
echo "==> Environment: ${DRONE_DEPLOY_TO}"
echo "==> Image version: ${IMAGE_VERSION}"
echo "==> Service: ${SERVICE_TO_DEPLOY}"

if [ ! -d "./${SERVICE_TO_DEPLOY}" ]; then
    echo "==> ERROR: ${SERVICE_TO_DEPLOY} is not a valid service directory"
    echo "===> Expected directory: ./${SERVICE_TO_DEPLOY}"
    exit 1
fi

for resource_type in "${resource_types[@]}"; do
    for resource_file in ./"${SERVICE_TO_DEPLOY}"/"${resource_type}".yaml; do
        if [ -f "${resource_file}" ]; then
            echo "===> Deploying resource: ${resource_file}"
            kd --file "${resource_file}" --debug-templates --dryrun
            kd --file "${resource_file}" \
               --timeout 12m
        fi
    done
done

echo "[info] Successfully deployed to environment ${KUBE_NAMESPACE}"
