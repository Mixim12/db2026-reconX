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
  instrument: yup
    .string()
    .required('Instrument is required'),
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
      instrument: '',
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
          <input
            {...register('instrument')}
            placeholder="SAP.DE or AAPL"
            aria-invalid={Boolean(errors.instrument)}
          />
        </label>
        {errors.instrument && (
          <p className="form-error" role="alert">
            {errors.instrument.message}
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
