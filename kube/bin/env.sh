#!/bin/bash

set -o nounset

# default values
: ${DRONE_DEPLOY_TO:="dev"}

# Cluster and deployment defaults
export CLUSTER="acp-notprod"
export KUBE_SERVER=https://kube-api-notprod.notprod.acp.homeoffice.gov.uk
export KUBE_TOKEN=${KUBE_TOKEN_ACP_NOTPROD}
export KUBE_NAMESPACE="rtge-dev"

# rt-emailer template variables
export RT_EMAILER_REPLICAS=1
export EMAIL_SENDER="rt-notprod@rtge-notprod.homeoffice.gov.uk"
export SES_SECRET_NAME=rtge-evw-dev-ses

# Dependent service hostnames used by rt-emailer
export CASEWORKER_DNS=caseworker-${DRONE_DEPLOY_TO}.rtge-notprod.homeoffice.gov.uk
export CASEWORKER_UKC_DNS=${CASEWORKER_DNS}
export RT_CUSTOMER_DNS=rt-customer-${DRONE_DEPLOY_TO}.rtge-notprod.homeoffice.gov.uk
export GE_CUSTOMER_DNS=ge-customer-${DRONE_DEPLOY_TO}.rtge-notprod.homeoffice.gov.uk

case ${DRONE_DEPLOY_TO} in
	'dev')
		export KUBE_NAMESPACE="rtge-dev"
	;;
	'uat')
		export KUBE_NAMESPACE="rtge-uat"
	;;
	'preprod')
		export KUBE_NAMESPACE="rtge-preprod"

		export RT_CUSTOMER_DNS=www-ppd2.faster-uk-entry.service.gov.uk
		export GE_CUSTOMER_DNS=ppd2.global-entry.beta.homeoffice.gov.uk
	;;
	'prod')
		export CLUSTER="acp-prod"
		export KUBE_NAMESPACE="rtge-prod"
		export KUBE_SERVER=https://kube-api-prod.prod.acp.homeoffice.gov.uk
		export KUBE_TOKEN=${KUBE_TOKEN_ACP_PROD}

		export CASEWORKER_UKC_DNS=rtcaseworker.homeoffice.gov.uk
		export RT_CUSTOMER_DNS=www.faster-uk-entry.service.gov.uk
		export GE_CUSTOMER_DNS=global-entry.beta.homeoffice.gov.uk

		export EMAIL_SENDER="no-reply@registered-traveller.homeoffice.gov.uk"
		export SES_SECRET_NAME=rtge-prod-ses
	;;
esac
