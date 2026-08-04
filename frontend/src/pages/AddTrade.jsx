// TICKET-ADV123 — React Hook Form + Yup validation.
import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { withAuth } from '@components/withAuth.jsx';
import { api } from '@services/apiService.js';

const schema = yup.object({
  tradeRef: yup
    .string()
    .required('Trade ref is required')
    .matches(/^[A-Z]{3}-\d{8}-\d{4}$/, 'Trade ref must match AAA-YYYYMMDD-NNNN'),
  instrumentId: yup
    .number()
    .typeError('Instrument is required')
    .required('Instrument is required'),
  counterpartyId: yup
    .number()
    .typeError('Counterparty is required')
    .required('Counterparty is required'),
  assetClass: yup
    .string()
    .required('Asset class is required'),
  side: yup
    .string()
    .required('Side is required'),
  quantity: yup
    .number()
    .typeError('Quantity must be a number')
    .positive('Quantity must be a positive number')
    .required('Quantity is required'),
  price: yup
    .number()
    .typeError('Price must be a number')
    .positive('Price must be a positive number')
    .required('Price is required'),
  tradeDate: yup
    .string()
    .required('Trade date is required'),
});

function AddTrade() {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
    reset,
  } = useForm({
    resolver: yupResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      tradeRef: '',
      instrumentId: '',
      counterpartyId: '',
      assetClass: '',
      side: '',
      quantity: '',
      price: '',
      tradeDate: '',
    },
  });

  const [serverError, setServerError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  async function onSubmit(values) {
    setServerError(null);
    setSuccessMessage(null);
    try {
      const payload = {
        ...values,
        instrumentId: Number(values.instrumentId),
        counterpartyId: Number(values.counterpartyId),
        quantity: Number(values.quantity),
        price: Number(values.price),
      };
      await api.createTrade(payload);
      setSuccessMessage('Trade created successfully!');
      reset();
    } catch (err) {
      setServerError(err.message || 'Failed to submit trade');
    }
  }

  return (
    <section>
      <h2>Add trade</h2>
      {successMessage && <div className="alert alert--success">{successMessage}</div>}
      {serverError && <div className="alert alert--danger" role="alert">{serverError}</div>}
      <form onSubmit={handleSubmit(onSubmit)} className="trade-form" noValidate>
        <label>
          Trade ref
          <input
            {...register('tradeRef')}
            placeholder="EQU-20260603-0001"
            aria-invalid={Boolean(errors.tradeRef)}
          />
        </label>
        {errors.tradeRef && (
          <p className="form-error" role="alert">
            {errors.tradeRef.message}
          </p>
        )}

        <label>
          Instrument
          <select
            {...register('instrumentId')}
            aria-invalid={Boolean(errors.instrumentId)}
          >
            <option value="">Select Instrument...</option>
            <option value="1">SAP.DE (SAP SE)</option>
            <option value="2">SIE.DE (Siemens AG)</option>
            <option value="3">DBKGn.DE (Deutsche Bank AG)</option>
            <option value="4">AAPL (Apple Inc)</option>
            <option value="5">MSFT (Microsoft Corp)</option>
          </select>
        </label>
        {errors.instrumentId && (
          <p className="form-error" role="alert">
            {errors.instrumentId.message}
          </p>
        )}

        <label>
          Counterparty
          <select
            {...register('counterpartyId')}
            aria-invalid={Boolean(errors.counterpartyId)}
          >
            <option value="">Select Counterparty...</option>
            <option value="1">Goldman Sachs International</option>
            <option value="2">JP Morgan Chase Bank</option>
            <option value="3">Morgan Stanley &amp; Co</option>
            <option value="4">Barclays Capital</option>
            <option value="5">Credit Suisse AG</option>
          </select>
        </label>
        {errors.counterpartyId && (
          <p className="form-error" role="alert">
            {errors.counterpartyId.message}
          </p>
        )}

        <label>
          Asset Class
          <select
            {...register('assetClass')}
            aria-invalid={Boolean(errors.assetClass)}
          >
            <option value="">Select Asset Class...</option>
            <option value="EQUITY">EQUITY</option>
            <option value="FIXED_INCOME">FIXED_INCOME</option>
            <option value="FX">FX</option>
          </select>
        </label>
        {errors.assetClass && (
          <p className="form-error" role="alert">
            {errors.assetClass.message}
          </p>
        )}

        <label>
          Side
          <select
            {...register('side')}
            aria-invalid={Boolean(errors.side)}
          >
            <option value="">Select Side...</option>
            <option value="BUY">BUY</option>
            <option value="SELL">SELL</option>
          </select>
        </label>
        {errors.side && (
          <p className="form-error" role="alert">
            {errors.side.message}
          </p>
        )}

        <label>
          Quantity
          <input
            type="number"
            step="any"
            {...register('quantity')}
            placeholder="1000"
            aria-invalid={Boolean(errors.quantity)}
          />
        </label>
        {errors.quantity && (
          <p className="form-error" role="alert">
            {errors.quantity.message}
          </p>
        )}

        <label>
          Price
          <input
            type="number"
            step="any"
            {...register('price')}
            placeholder="125.50"
            aria-invalid={Boolean(errors.price)}
          />
        </label>
        {errors.price && (
          <p className="form-error" role="alert">
            {errors.price.message}
          </p>
        )}

        <label>
          Trade date
          <input
            type="date"
            {...register('tradeDate')}
            aria-invalid={Boolean(errors.tradeDate)}
          />
        </label>
        {errors.tradeDate && (
          <p className="form-error" role="alert">
            {errors.tradeDate.message}
          </p>
        )}

        <button disabled={isSubmitting} type="submit">
          {isSubmitting ? 'Submitting...' : 'Submit'}
        </button>
      </form>
    </section>
  );
}

export default withAuth(AddTrade);
